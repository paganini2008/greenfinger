# Greenfinger — front end

The web interface: an Angular application that talks to `greenfinger-api` and nothing else. What
it is for is in the [root README](../README.md); this is for working on it.

## Stack

Angular 21 with signals — no NgRx, no RxJS store. Angular Material for the components, Tailwind for
layout, a green-and-white theme. Standalone components throughout; there is no `NgModule`.

The whole application is `greenfinger-ui/`. Everything below is run from there.

## Working on it

``` shell
cd greenfinger-ui
npm install
npm start            # http://localhost:4200, proxying the api to a node you started yourself
```

It needs a node to talk to — `./run-local.sh` in `deploy/`, which listens on 50080. The dev
configuration allows that origin; a node that has not been told about it answers `403 Invalid CORS
request`, which in a browser looks exactly like a wrong password.

``` shell
npm test             # unit tests, 31 of them
npm run lint
npm run e2e          # Playwright, when there is a node running to point it at
```

## Building for deployment

``` shell
npm run build:deploy
```

Builds, then copies the result into `deploy/docker/static/` together with the front end
container's `Dockerfile.web` and `server.js`. `run-docker.sh` picks it up from there and serves it
on 9700 — a small zero-dependency Node server that serves the app and forwards `/v2` and
`/actuator` to whichever node answers, so the browser talks to the cluster rather than to one node.

Without that directory `run-docker.sh` says so and starts the nodes alone; the app is then reached
through a node directly, which works and gives up the load spreading.

## Configuration

One setting, and it is read at runtime rather than baked into the build: `window.__GF__.apiBaseUrl`
in `env.js`, written by whatever deploys the app. Empty means "the same origin", which is right
behind the front end container and behind nginx or Kong. Everything else the page needs it asks
the api for.

Tokens are signed by the api and stateless — the front end holds one and sends it; there is no
session to keep on either side.
