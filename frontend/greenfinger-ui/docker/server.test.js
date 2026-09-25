/**
 * The front end server's decisions, tested where they are made.
 *
 * This file exists because of the bug it starts with. The proxy forwarded the browser's Origin
 * header to a node on another port, the node correctly called it a cross-origin request, and
 * every button in the page that wrote anything answered 403 while every page that only read
 * carried on. It reached a release because the one line that decides it could not be called: the
 * server was a script that started listening on import, and the e2e suite points at a node
 * directly, where the page and the api share an origin and the header never appears.
 *
 *   node --test docker/server.test.js
 */
const test = require('node:test');
const assert = require('node:assert');

const { upstreamHeaders, membersFrom, pinnedNode, withoutNodeParam } = require('./server');

const upstream = { host: 'localhost', port: 50080 };

test('the node is told its own host, not this one', () => {
  const headers = upstreamHeaders({ headers: { host: 'localhost:9700' } }, upstream);

  assert.equal(headers.host, 'localhost:50080');
});

test('a same-origin Origin is dropped rather than forwarded', () => {
  // Chrome puts Origin on every same-origin POST. Forwarded untouched it told the node that a
  // page on :9700 was calling an api on :50080 -- a cross-origin request from an address it has
  // no reason to allow.
  const headers = upstreamHeaders(
    { headers: { host: 'localhost:9700', origin: 'http://localhost:9700' } },
    upstream,
  );

  assert.equal(headers.origin, undefined);
});

test('an Origin from anywhere else is forwarded exactly as it arrived', () => {
  // a genuine cross-origin call, for the node to allow or refuse on its own terms
  const headers = upstreamHeaders(
    { headers: { host: 'localhost:9700', origin: 'http://evil.example' } },
    upstream,
  );

  assert.equal(headers.origin, 'http://evil.example');
});

test('an Origin that is not a url is left alone rather than thrown over', () => {
  const headers = upstreamHeaders(
    { headers: { host: 'localhost:9700', origin: 'null' } },
    upstream,
  );

  assert.equal(headers.origin, 'null');
});

test('every other header travels untouched', () => {
  const headers = upstreamHeaders(
    {
      headers: {
        host: 'localhost:9700',
        authorization: 'Bearer token',
        'content-type': 'application/json',
      },
    },
    upstream,
  );

  assert.equal(headers.authorization, 'Bearer token');
  assert.equal(headers['content-type'], 'application/json');
});

test('?__node= pins a request to one node and is not passed on', () => {
  assert.equal(pinnedNode('/v2/catalog?__node=2'), 2);
  assert.equal(pinnedNode('/v2/catalog'), null);
  // a number that is not one is an error rather than "any node"
  assert.equal(pinnedNode('/v2/catalog?__node=abc'), -1);

  assert.equal(withoutNodeParam('/v2/catalog?__node=2'), '/v2/catalog');
  assert.equal(withoutNodeParam('/v2/catalog?__node=2&q=rust'), '/v2/catalog?q=rust');
});

test('the members of a cluster are read out of one node health answer', () => {
  const health = JSON.stringify({
    components: {
      spreaderCluster: {
        details: {
          // the answering node's own http port, beside the cluster details
          metadata: { 'server.port': '50080' },
          otherMembers: [
            {
              address: '127.0.0.1:22001',
              state: 'ALIVE',
              metadata: { 'server.port': '50081' },
            },
            // a node that has left is not somewhere to send a request
            {
              address: '127.0.0.1:22002',
              state: 'LEFT',
              metadata: { 'server.port': '50082' },
            },
          ],
        },
      },
    },
  });

  const members = membersFrom(Buffer.from(health), upstream);

  assert.ok(members.some((one) => one.port === 50081));
  // the node that answered is in the list as well, by the spelling it was asked by
  assert.ok(members.some((one) => one.port === 50080));
  // and one that has left is not
  assert.ok(!members.some((one) => one.port === 50082));
});

test('a health answer that is not what we expect is no members, not a crash', () => {
  assert.deepEqual(membersFrom(Buffer.from('not json'), upstream), []);
  assert.deepEqual(membersFrom(Buffer.from('{}'), upstream), []);
});
