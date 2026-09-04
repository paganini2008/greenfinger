/**
 * The one thing about this app that is decided where it runs rather than where it is built.
 *
 * Loaded before the bundle, from the same directory as index.html, so changing it is editing one
 * line in the served files -- no rebuild. run-docker.sh writes it from GF_API_BASE_URL in .env;
 * left as it ships, it means "the api is on this origin", which is true both for the dev server
 * (it proxies /v2) and for the api jar (it serves the page and the api itself).
 *
 * Only ever apiBaseUrl. Everything else the front end needs is either the api's answer or a
 * mirror of a backend enum, and neither is anybody's to configure.
 */
window.__GF__ = { apiBaseUrl: '' };
