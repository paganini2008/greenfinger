# Greenfinger front end

An Angular application that talks to `greenfinger-api` and nothing else. What it is for is in the
[root README](../README.md). This is how it is built.

## Stack

Angular 21 with signals. No NgRx and no RxJS store. Angular Material for the components, Tailwind
for layout, a green and white theme. Standalone components throughout, so there is no `NgModule`.

The whole application is `greenfinger-ui/`. Everything below runs from there.

## Layout

``` text
src/app
├── app.ts / app.html / app.scss   the shell: nav rail, crawl banner, footer, profile badge
├── app.routes.ts                  eight lazy routes, each behind authGuard
├── core
│   ├── api.service.ts             every call the app makes, one method each
│   ├── api.models.ts              the api's shapes, mirrors of backend enums
│   ├── api.config.ts              API_PREFIX, read from window.__GF__ at runtime
│   ├── auth.service.ts            the token, and /me as the authority on it
│   ├── auth.guard.ts              signed in, and role aware for the pages that write
│   ├── auth.interceptor.ts        attaches the token, and 401 sends you to login
│   └── notify.service.ts          snackbars
├── pages
│   ├── dashboard                  what was kept, what was thrown away
│   ├── catalogs                   the list, with live progress on a crawling card
│   ├── catalog-edit               the form, one url required and defaults for the rest
│   ├── monitor                    live view, run report, per node table, delete panel
│   ├── resources                  rows in crawl order, expandable
│   ├── search                     three modes over one box
│   ├── cluster                    members, health checks, merged settings
│   └── login
└── shared                         confirm dialog, crawl state dialog, sparkline
```

## How state works

`signal()` for what a component owns, `computed()` for anything derived, and no store in between.
A page loads by calling `ApiService`, setting a signal, and letting the template read it.

``` ts
protected readonly catalogs = signal<Catalog[]>([]);

protected readonly meaningReady = computed(() =>
  this.inScope().some(
    (one) => (one.searchVersion ?? -1) >= 0 && (one.outputTypes ?? []).includes('vector'),
  ),
);
```

Live pages poll rather than hold a socket. The monitor asks for the summary on a timer while a
crawl is registered and stops when it is not. The crawl banner and the health badge poll slowly,
because they are banners rather than progress bars.

Templates use the built in control flow, `@if` and `@for`, with `track` on every loop.

## Conventions

- **Copy is design content.** Sentence case, active voice, and a button that says Publish produces
  a toast that says Published. Empty states say what to do next rather than only what is missing.
- **No dashes and no semicolons in anything a person reads on screen.** Use a full stop, a colon,
  or a comma. They are the clearest tell of generated text.
- **A mode the data cannot answer is greyed, not hidden.** Hiding it makes the row reflow and
  teaches nothing. Greying it leaves room for a tooltip that says why.
- Colours come from tokens on `:root`. Nothing hard codes a hex value in a component.

## Working on it

``` shell
cd greenfinger-ui
npm install
npm start            # http://localhost:4200, proxying the api to a node you started yourself
```

It needs a node to talk to, so `./run-local.sh` in `deploy/` first, which listens on 50080. The dev
configuration allows that origin. A node that has not been told about it answers
`403 Invalid CORS request`, which in a browser looks exactly like a wrong password.

``` shell
npm test             # unit tests
npm run lint
npm run e2e          # Playwright, 21 specs, against a running node
```

The e2e suite creates its own corpus through the REST api in `e2e/global-setup.ts` and leaves it
in place, so the second run costs no crawling. A suite that depends on a catalog somebody happened
to have is a suite that stops working.

## Building for deployment

``` shell
npm run build:deploy
```

Builds, then copies the result into `deploy/docker/static/` together with the front end container's
`Dockerfile.web` and `server.js`. `run-docker.sh` picks it up and serves it on 9700 with a small
Node server that has no dependencies, forwarding `/v2` and `/actuator` to whichever node answers,
so the browser talks to the cluster rather than to one node.

Without that directory `run-docker.sh` says so and starts the nodes alone. The app is then reached
through a node directly, which works and gives up the load spreading.

## Configuration

One setting, read at runtime rather than baked into the build.

``` js
// deploy/docker/static/env.js, written by whatever deploys the app
window.__GF__ = { apiBaseUrl: '' };
```

Empty means the same origin, which is right behind the front end container and behind nginx or
Kong. Everything else the page needs it asks the api for.

Tokens are signed by the api and stateless. The front end holds one and sends it, and there is no
session to keep on either side.
