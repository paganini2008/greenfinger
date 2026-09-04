/**
 * Where the api lives.
 *
 * Same origin by default, which is true in both the ways this app is normally served: the dev
 * server proxies `/v2` to the backend, and the api jar serves the page and the api from one
 * process. Neither needs a host configured, and neither needs CORS.
 *
 * The exception is the front end container, which is a separate origin from the nodes it talks
 * to. It is given the address at run time, in `env.js` beside `index.html`, so pointing it
 * somewhere else is editing one line of the served files rather than rebuilding the app --
 * `run-docker.sh` writes that file from `GF_API_BASE_URL` in `.env`.
 *
 * The `/v2` suffix is not configurable: it is the api's version, which is versioned with the
 * product, and it matches `GF_API_PREFIX` on the backend. 2.x speaks `/v2`.
 */
declare global {
  interface Window {
    __GF__?: { apiBaseUrl?: string };
  }
}

/** No trailing slash, so joining is always one `/` and never two. */
function baseUrl(): string {
  const configured = (window.__GF__?.apiBaseUrl ?? '').trim();
  return configured.replace(/\/+$/, '');
}

export const API_PREFIX = `${baseUrl()}/v2`;
