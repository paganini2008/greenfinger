/**
 * The dev server's route to the api.
 *
 * A file rather than the json one it replaces, so the port can come from the environment. 50080 is
 * where run-local.sh puts the first node, and 8080 is not: on a developer's machine 8080 is
 * usually taken already -- nginx, a repository manager, another project -- which is why the
 * server does not ask for it. A hard coded port that is wrong turns every request into a 502, or
 * worse into somebody else's 404, with nothing in the page to explain either.
 *
 *     GF_API_PORT=50081 npm start          # the second node, say
 *
 * Production needs none of this: the same jar serves the page and the api from one origin, which
 * is why the front end only ever uses the relative prefix.
 */
const port = process.env.GF_API_PORT || '50080';

const api = {
  target: `http://localhost:${port}`,
  secure: false,
  changeOrigin: false,
  logLevel: 'warn',
};

// /actuator as well as /v2: the cluster page reads the node's health and its channel metrics from
// Spring's actuator, which does not sit under the api prefix and never will.
module.exports = {
  '/v2': api,
  '/actuator': api,
};
