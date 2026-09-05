/**
 * The front end container: the built app, and one address in front of however many nodes.
 *
 * Node rather than nginx, and no dependencies at all -- not express, not http-proxy. What this
 * has to do is serve a directory, fall back to index.html, and forward two prefixes; Node's own
 * http module does all three, and a package.json here would mean an npm install in the image, a
 * lockfile to keep current, and a supply chain for a hundred lines of code.
 *
 * Environment:
 *   GF_UPSTREAMS   host:port,host:port -- where to start looking. One entry is enough: the rest
 *                  of the cluster is discovered from it. See discover().
 *   GF_DISCOVER    0 turns that off and treats GF_UPSTREAMS as the whole list.
 *   PORT           what to listen on. 80 in the container.
 *   GF_STATIC      where the built app is. /app/static in the image.
 */
'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const PORT = Number(process.env.PORT || 80);
const STATIC = process.env.GF_STATIC || '/app/static';
/**
 * Where to start looking, from the environment. Not the list of nodes -- the seeds it is found
 * from; see discover().
 */
const SEEDS = (process.env.GF_UPSTREAMS || '')
  .split(',')
  .map((one) => one.trim())
  .filter(Boolean)
  .map((one) => {
    const [host, port] = one.split(':');
    return { host, port: Number(port || 8080) };
  });

const DISCOVER = process.env.GF_DISCOVER !== '0';

/** How often the cluster is asked who is in it, and how long one such ask may take. */
const DISCOVERY_INTERVAL = 10_000;
const DISCOVERY_TIMEOUT = 2_000;

/**
 * The nodes as they are right now.
 *
 * Starts as the seeds and is replaced by what the cluster says about itself. Every node knows its
 * members and, since each one publishes its http port as cluster metadata, knows how to reach
 * them -- so one address is enough to find all of them, and a node started later is picked up
 * within a poll without anybody editing a list or restarting this process.
 *
 * That matters more than it sounds. Written out by hand, the addresses have to be right before
 * anything starts, every node needs a port fixed in advance, and a fourth node is invisible until
 * somebody remembers this file. Discovered, the front end needs one entry point and the nodes can
 * take whatever ports they are given.
 */
let UPSTREAMS = SEEDS.slice();

/** Everything the api owns. The rest of the paths are the app's own routes. */
const PROXIED = /^\/(v2|actuator)\//;

/**
 * How a caller asks for one particular node instead of whichever is next.
 *
 * Spreading requests is right for everything the app does with the api -- any node can answer for
 * the cluster, and spreading is the point of putting one address in front of them. It is wrong for
 * exactly one page: System health is a node's own account of itself, its counters, its buffers,
 * its uptime, and round robin turned that into three nodes' numbers interleaved. The values jumped
 * between polls, the chart was a mix of three machines, and a warning about one node appeared and
 * vanished every three seconds.
 *
 * So a request carrying ?__node=<index> goes to that node and no other, and is not retried
 * elsewhere: "how is node 2" has no answer from node 3. The parameter is stripped before the
 * request is forwarded, because it belongs to this hop.
 */
const NODE_PARAM = '__node';

/** What the picker on that page is populated from. Absent when this is not the proxy talking. */
const NODES_PATH = '/__nodes';

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};

let next = 0;

/** Bodies larger than this are streamed and never retried. See collectBody. */
const MAX_REPLAYABLE_BODY = 1024 * 1024;

/**
 * Round robin, and on a connection failure the next one is tried.
 *
 * Only a request that never reached a node is retried: one that was answered, however it was
 * answered, belongs to that node, and sending a second copy of a crawl request to a second node
 * because the first was slow would start the crawl twice.
 *
 * Retrying means sending the body again, and a request is a stream that can only be read once --
 * piping it into the second node hands over a stream that has already been drained, so the node
 * waits for a body that will never arrive until something times out. That is invisible on GET,
 * which has no body, and turns every sign-in that happens to reach a dead node into a hang: the
 * page reports a failed login, and the reason is a node that was not even asked properly.
 *
 * So the body is collected first, and replayed on each attempt. Up to a limit -- a request bigger
 * than that is piped straight through and not retried, because holding an arbitrary upload in
 * memory to make a retry possible is the worse trade.
 */
function proxy(request, response) {
  if (UPSTREAMS.length === 0) {
    response.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' });
    response.end('No GF_UPSTREAMS configured, so there is no api to forward to.\n');
    return;
  }
  // ?__node=<index> pins the request; anything else is spread as usual
  const asked = pinnedNode(request.url);
  const path = asked === null ? request.url : withoutNodeParam(request.url);
  const order = [];
  if (asked !== null) {
    if (asked < 0 || asked >= UPSTREAMS.length) {
      response.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      response.end(`There is no node ${asked}; this front end has ${UPSTREAMS.length}.\n`);
      return;
    }
    // one entry, so the retry loop below has nowhere else to go -- which is the point
    order.push(UPSTREAMS[asked]);
  } else {
    for (let i = 0; i < UPSTREAMS.length; i++) {
      order.push(UPSTREAMS[(next + i) % UPSTREAMS.length]);
    }
    next = (next + 1) % UPSTREAMS.length;
  }

  const attempt = (index, body) => {
    const upstream = order[index];
    const forwarded = http.request(
      {
        host: upstream.host,
        port: upstream.port,
        method: request.method,
        path,
        headers: { ...request.headers, host: `${upstream.host}:${upstream.port}` },
      },
      (answer) => {
        response.writeHead(answer.statusCode || 502, answer.headers);
        answer.pipe(response);
      },
    );
    // a crawl holds the connection open for as long as the crawl runs
    forwarded.setTimeout(3600_000, () => forwarded.destroy(new Error('upstream timed out')));
    forwarded.on('error', (failure) => {
      if (index + 1 < order.length && !response.headersSent && body !== null) {
        attempt(index + 1, body);
        return;
      }
      if (!response.headersSent) {
        response.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' });
      }
      response.end(`No node answered: ${failure.message}\n`);
    });
    if (body === null) {
      // too big to hold, so this is the only attempt it gets
      request.pipe(forwarded);
    } else {
      if (body.length > 0) {
        forwarded.write(body);
      }
      forwarded.end();
    }
  };

  collectBody(request, (body) => attempt(0, body));
}

/**
 * Ask the cluster who is in it, and take that as the list.
 *
 * `/actuator/health` needs no credentials -- it is what a container orchestrator polls -- and its
 * spreaderCluster component carries what is needed: this node's own http port in `metadata`, and
 * every other member's address together with its own `metadata['server.port']`. The cluster's
 * addresses are what a node advertises, which is the address other nodes reach it on, so it is
 * also the address to forward to.
 *
 * Tried against each known node in turn and the first answer wins: a node that has stopped
 * answering cannot say who else exists, and one that is alive can. Nothing is changed when none
 * of them answers -- the previous list is a better guess than an empty one, and a front end that
 * emptied its own upstreams during a restart would answer 502 to a cluster that was merely busy
 * coming back.
 */
function discover() {
  // Whoever is known, and then the seeds again for the ones already known. Asking the current
  // list first is what lets the entry point itself be killed: the node it named is gone, the two
  // it introduced are not, and either can say who is left. Keeping the seeds on the end is the
  // other direction -- when every discovered node has gone, the address this was started with is
  // the only one left to try, and it may well be back.
  const seen = new Set();
  const candidates = [];
  for (const one of [...UPSTREAMS, ...SEEDS]) {
    const key = `${one.host}:${one.port}`;
    if (!seen.has(key)) {
      seen.add(key);
      candidates.push(one);
    }
  }
  const ask = (index) => {
    if (index >= candidates.length) {
      return;
    }
    const upstream = candidates[index];
    const request = http.request(
      {
        host: upstream.host,
        port: upstream.port,
        path: '/actuator/health',
        method: 'GET',
        timeout: DISCOVERY_TIMEOUT,
      },
      (answer) => {
        const chunks = [];
        answer.on('data', (chunk) => chunks.push(chunk));
        answer.on('end', () => {
          const found = membersFrom(Buffer.concat(chunks), upstream);
          if (found.length) {
            adopt(found);
          } else {
            ask(index + 1);
          }
        });
      },
    );
    request.on('timeout', () => request.destroy(new Error('timed out')));
    request.on('error', () => ask(index + 1));
    request.end();
  };
  ask(0);
}

/** The members in a health answer, as host and http port. Empty when it does not say. */
function membersFrom(body, asked) {
  let health;
  try {
    health = JSON.parse(body.toString('utf8'));
  } catch {
    return [];
  }
  const details = health?.components?.spreaderCluster?.details;
  if (!details) {
    return [];
  }
  const port = (member) => Number(member?.metadata?.['server.port']);
  // 'localhost' and '127.0.0.1' are the same machine, and mixing the two spellings in one list
  // reads as two different places -- and makes the node picker on System health look like it is
  // offering a set it is not. Normalised to the numeric form, which is also the one that cannot
  // resolve to ::1 on a host where nothing is listening there.
  const same = (name) => (name === 'localhost' ? '127.0.0.1' : name);
  const host = (address) => same(String(address || '').split(':')[0]);
  const members = [];
  // the node that answered: its cluster address is the interface it advertises, which for a
  // single machine is 127.0.0.1 and is not necessarily how this process reached it, so its own
  // entry keeps the host that was actually asked
  const own = port(details);
  if (own) {
    members.push({ host: same(asked.host), port: own });
  }
  for (const member of details.otherMembers || []) {
    const theirs = port(member);
    if (theirs && member.state === 'ALIVE') {
      members.push({ host: host(member.address) || asked.host, port: theirs });
    }
  }
  // a stable order, so ?__node=1 means the same node from one poll to the next
  return members.sort((a, b) => a.host.localeCompare(b.host) || a.port - b.port);
}

/** Replace the list, and say so when it actually changed. */
function adopt(found) {
  const before = UPSTREAMS.map((one) => `${one.host}:${one.port}`).join(',');
  const after = found.map((one) => `${one.host}:${one.port}`).join(',');
  if (before === after) {
    return;
  }
  UPSTREAMS = found;
  next = 0;
  console.log(`nodes: ${after}`);
}

/** The index in ?__node=<index>, or null when the caller did not ask for one. */
function pinnedNode(url) {
  const value = new URL(url, 'http://x').searchParams.get(NODE_PARAM);
  if (value === null) {
    return null;
  }
  const index = Number(value);
  return Number.isInteger(index) ? index : -1;
}

/** The same url with the parameter removed, because it was addressed to this hop. */
function withoutNodeParam(url) {
  const parsed = new URL(url, 'http://x');
  parsed.searchParams.delete(NODE_PARAM);
  return parsed.pathname + (parsed.searchParams.size ? `?${parsed.searchParams}` : '');
}

/**
 * Reads the request body so it can be sent more than once.
 *
 * Calls back with null when it grows past the limit, which means "do not retry this one" -- by
 * then the beginning of it has already been handed to the first node, and the alternative is
 * buffering an upload of unknown size to keep an option nobody may need.
 */
function collectBody(request, done) {
  if (request.method === 'GET' || request.method === 'HEAD') {
    done(Buffer.alloc(0));
    return;
  }
  const chunks = [];
  let size = 0;
  let oversized = false;
  request.on('data', (chunk) => {
    if (oversized) {
      return;
    }
    size += chunk.length;
    if (size > MAX_REPLAYABLE_BODY) {
      oversized = true;
      chunks.length = 0;
      done(null);
      return;
    }
    chunks.push(chunk);
  });
  request.on('end', () => {
    if (!oversized) {
      done(Buffer.concat(chunks));
    }
  });
  request.on('error', () => {
    if (!oversized) {
      done(Buffer.alloc(0));
    }
  });
}

/** Refuses anything that climbs out of the static directory. */
function resolve(urlPath) {
  const decoded = decodeURIComponent(urlPath.split('?')[0]);
  const resolved = path.resolve(STATIC, '.' + path.posix.normalize(decoded));
  return resolved.startsWith(STATIC) ? resolved : null;
}

function serve(file, response, fallbackToApp) {
  fs.stat(file, (failure, stat) => {
    if (failure || !stat.isFile()) {
      if (fallbackToApp) {
        // a single page app: anything that is not a file is one of the app's own routes
        serve(path.join(STATIC, 'index.html'), response, false);
        return;
      }
      response.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      response.end('not found\n');
      return;
    }
    const extension = path.extname(file).toLowerCase();
    const headers = { 'content-type': TYPES[extension] || 'application/octet-stream' };
    // The build is content hashed except for these two, and a browser that caches either keeps
    // loading last week's app against this week's api.
    const name = path.basename(file);
    headers['cache-control'] =
      name === 'index.html' || name === 'env.js' ? 'no-store' : 'public, max-age=31536000';
    response.writeHead(200, headers);
    fs.createReadStream(file).pipe(response);
  });
}

http
  .createServer((request, response) => {
    if ((request.url || '').split('?')[0] === NODES_PATH) {
      // What the System health page's node picker reads. Only the proxy knows the list -- a node
      // knows its cluster's members but not which of them this front end was pointed at, and it is
      // this front end's addresses that a browser can actually reach.
      const body = JSON.stringify(
        UPSTREAMS.map((one, index) => ({ index, address: `${one.host}:${one.port}` })),
      );
      response.writeHead(200, {
        'content-type': 'application/json; charset=utf-8',
        'cache-control': 'no-store',
      });
      response.end(body);
      return;
    }
    if (PROXIED.test(request.url || '')) {
      proxy(request, response);
      return;
    }
    const file = resolve(request.url || '/');
    if (!file) {
      response.writeHead(400, { 'content-type': 'text/plain; charset=utf-8' });
      response.end('bad path\n');
      return;
    }
    serve(file, response, true);
  })
  .listen(PORT, () => {
    if (DISCOVER && SEEDS.length) {
      discover();
      // unref'd, so this timer alone never keeps the process alive
      setInterval(discover, DISCOVERY_INTERVAL).unref();
    }
    console.log(
      `greenfinger web on ${PORT}, serving ${STATIC}, api to ` +
        (UPSTREAMS.map((one) => `${one.host}:${one.port}`).join(', ') || '(nothing configured)'),
    );
  });
