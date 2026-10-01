# Greenfinger front end

**An Angular application that talks to `greenfinger-api` and nothing else.**

What it is for is in the [root README](../README.md). This is how it is built.

![The console](../docs/blogger/assets/monitor-live.jpg)

## Table of contents

[Stack](#stack) · [Layout](#layout) · [Architecture](#architecture) · [Requirements](#requirements) · [Quick start](#quick-start) · [Examples](#examples) · [Configuration](#configuration) · [Performance](#performance) · [Conventions](#conventions)

---

## Stack

| | Version | Used for |
|---|---|---|
| **Angular** | 21.2 | Signals throughout. No NgRx and no RxJS store |
| **Angular Material** | 21.2 | Components |
| **Tailwind** | 4.3 | Layout, and everything around the components |
| **TypeScript** | 5.9 | |
| **Vitest** | 4.0 | Unit tests |
| **Playwright** | 1.62 | End to end tests |

Standalone components throughout, so there is no `NgModule`. The whole application is
`greenfinger-ui/`, and everything below runs from there.

---

## Layout

``` text
src/app
├── app.ts / app.html / app.scss   the shell: nav rail, crawl banner, footer, profile badge
├── app.routes.ts                  eight lazy routes, each behind authGuard
├── core                           7 files
│   ├── api.service.ts             every call the app makes, one method each
│   ├── api.models.ts              the api's shapes, mirrors of backend enums
│   ├── api.config.ts              API_PREFIX, read from window.__GF__ at runtime
│   ├── auth.service.ts            the token, and /me as the authority on it
│   ├── auth.guard.ts              signed in, and role aware for the pages that write
│   ├── auth.interceptor.ts        attaches the token, and 401 sends you to login
│   └── notify.service.ts          snackbars
├── pages                          8 pages
│   ├── dashboard                  what was kept, what was thrown away
│   ├── catalogs                   the list, with live progress on a crawling card
│   ├── catalog-edit               the form. One url required, defaults for the rest
│   ├── monitor                    live view, run report, per node table, delete panel
│   ├── resources                  rows in crawl order, expandable
│   ├── search                     three modes over one box
│   ├── cluster                    members, health checks, merged settings
│   └── login
└── shared                         confirm dialog, crawl state dialog, sparkline
```

---

## Architecture

### How state works

``` text
   component ──► ApiService ──► /v2/... ──► signal()
                                              │
                                     computed() derives
                                              │
                                     template reads it
```

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

### How it reaches the cluster

``` text
   browser ──► :9700 ──► server.js ──► whichever node answers
                           │
               a small Node server with no dependencies.
               Serves the app, forwards /v2 and /actuator
```

In development the Angular dev server proxies instead, and a node that has not been told about the
origin answers `403 Invalid CORS request`, which in a browser looks exactly like a wrong password.

---

## Requirements

| | Version | Needed for |
|---|---|---|
| **Node** | 20 or later | Everything |
| **A running node** | `./run-local.sh` in `deploy/` | `npm start` and `npm run e2e` |

The unit tests need nothing running. The dev server and the e2e suite both need an api to talk to.

---

## Quick start

``` shell
cd greenfinger-ui
npm install
npm start            # http://localhost:4200, proxying the api to a node you started yourself
```

Expected output:

``` text
➜  Local:   http://localhost:4200/
Application bundle generation complete. [3.3 seconds]
```

``` shell
npm test             # unit tests
npm run lint
npm run e2e          # Playwright, against a running node
npm run build:deploy # build, and copy into deploy/docker/static/
```

---

## Examples

### Add a call to the api

**Input.** A new endpoint on the backend.

**Code.** One method on `ApiService`, unwrapping the envelope so a `success: false` becomes an
error rather than an empty result.

``` ts
listCategories(): Observable<string[]> {
  return this.unwrap(this.http.get<ApiResult<string[]>>(`${API_PREFIX}/catalog/cats`));
}
```

**Output.** Every page uses the same method. There is no second place a url is written down.

### Derive instead of storing

**Input.** A flag that depends on data you already have.

**Code.** `computed()`, never a second signal kept in step by hand.

``` ts
protected readonly nothingIndexed = computed(
  () =>
    this.catalogs().length > 0 &&
    !this.catalogs().some(
      (c) => (c.searchVersion ?? -1) >= 0 && (c.outputTypes ?? []).includes('index'),
    ),
);
```

**Output.** The template reads `nothingIndexed()` and it is correct by construction. A second
signal would be one more thing to forget to update.

### Build for the container

**Input.** A finished change.

``` shell
npm run build:deploy
```

**Output.** Builds, then copies the result into `deploy/docker/static/` together with the front end
container's `Dockerfile.web` and `server.js`. `run-docker.sh` picks it up and serves it on 9700.

Without that directory `run-docker.sh` says so and starts the nodes alone. The app is then reached
through a node directly, which works and gives up the load spreading.

---

## Configuration

One setting, read at runtime rather than baked into the build.

``` js
// deploy/docker/static/env.js, written by whatever deploys the app
window.__GF__ = { apiBaseUrl: '' };
```

| Value | Means |
|---|---|
| empty | The same origin. Right behind the front end container, and behind nginx or Kong |
| a url | An api somewhere else |

Everything else the page needs it asks the api for. Tokens are signed by the api and stateless, so
the front end holds one and sends it, and there is no session to keep on either side.

Three development only keys live in `frontend/.env` and are deliberately **not** copied into
`deploy/`: `GF_VERSION`, `GF_DEV_PORT` and `GF_API_PORT`. Nothing reads them at deploy time, and
adding them back for symmetry would be a mistake.

---

## Performance

**Bundle.**

| | Size |
|---|---|
| Initial total | 738.55 kB |
| Transferred | 169.57 kB |
| Build time | 3.3 s |

Eight routes are lazy, so a page costs only what it uses. The largest chunks are Material and the
monitor page's charts, which are hand drawn SVG rather than a charting library.

**Tests.**

| | |
|---|---|
| Unit | 27 tests in 4 spec files, around 1.4 s |
| End to end | 21 Playwright specs in 4 files |

The e2e suite builds its own corpus through the REST api in `e2e/global-setup.ts` and leaves it in
place, so the second run costs no crawling. A suite that depends on a catalog somebody happened to
have is a suite that stops working.

---

## Conventions

- **Copy is design content.** Sentence case, active voice, and a button that says Publish produces
  a toast that says Published. Empty states say what to do next rather than only what is missing.
- **No dashes and no semicolons in anything a person reads on screen.** Use a full stop, a colon,
  or a comma. They are the clearest tell of generated text. This covers strings built in `.ts`
  files, not only templates.
- **A mode the data cannot answer is greyed, not hidden.** Hiding it makes the row reflow and
  teaches nothing. Greying it leaves room for a tooltip that says why.
- **Colours come from tokens on `:root`.** Nothing hard codes a hex value in a component.
- **Screenshots in the docs are real.** They come from an actual run, taken in a browser, not
  mocked up.

Apache License 2.0. See [LICENSE](../LICENSE).
