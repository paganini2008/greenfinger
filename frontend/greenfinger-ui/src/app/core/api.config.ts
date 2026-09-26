/**
 * Where the api lives. Same origin by default, which covers the dev server and the api jar
 * serving both. The front end container is the exception and is given an address at run time, in
 * `env.js` beside `index.html`. `/v2` is the api's version and is not configurable.
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
