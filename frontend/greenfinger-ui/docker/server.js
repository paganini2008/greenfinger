/**
 * The front end container: the built app, and one address in front of however many nodes.
 *
 * Node rather than nginx, and no dependencies at all -- not express, not http-proxy. What this
 * has to do is serve a directory, fall back to index.html, and forward two prefixes; Node's own
 * http module does all three, and a package.json here would mean an npm install in the image, a
 * lockfile to keep current, and a supply chain for a hundred lines of code.
 *
 * Environment:
 *   GF_UPSTREAMS   host:port,host:port -- the nodes. Requests are spread across them.
 *   PORT           what to listen on. 80 in the container.
 *   GF_STATIC      where the built app is. /app/static in the image.
 */
'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const PORT = Number(process.env.PORT || 80);
const STATIC = process.env.GF_STATIC || '/app/static';
const UPSTREAMS = (process.env.GF_UPSTREAMS || '')
  .split(',')
  .map((one) => one.trim())
  .filter(Boolean)
  .map((one) => {
    const [host, port] = one.split(':');
    return { host, port: Number(port || 8080) };
  });

/** Everything the api owns. The rest of the paths are the app's own routes. */
const PROXIED = /^\/(v2|actuator)\//;

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

/**
 * Round robin, and on a connection failure the next one is tried.
 *
 * Only a request that never reached a node is retried: one that was answered, however it was
 * answered, belongs to that node, and sending a second copy of a crawl request to a second node
 * because the first was slow would start the crawl twice.
 */
function proxy(request, response) {
  if (UPSTREAMS.length === 0) {
    response.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' });
    response.end('No GF_UPSTREAMS configured, so there is no api to forward to.\n');
    return;
  }
  const order = [];
  for (let i = 0; i < UPSTREAMS.length; i++) {
    order.push(UPSTREAMS[(next + i) % UPSTREAMS.length]);
  }
  next = (next + 1) % UPSTREAMS.length;

  const attempt = (index) => {
    const upstream = order[index];
    const forwarded = http.request(
      {
        host: upstream.host,
        port: upstream.port,
        method: request.method,
        path: request.url,
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
      if (index + 1 < order.length && !response.headersSent) {
        attempt(index + 1);
        return;
      }
      if (!response.headersSent) {
        response.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' });
      }
      response.end(`No node answered: ${failure.message}\n`);
    });
    request.pipe(forwarded);
  };
  attempt(0);
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
    console.log(
      `greenfinger web on ${PORT}, serving ${STATIC}, api to ` +
        (UPSTREAMS.map((one) => `${one.host}:${one.port}`).join(', ') || '(nothing configured)'),
    );
  });
