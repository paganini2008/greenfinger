/**
 * The front end's version, from one place that is not this file.
 *
 * There were two copies of the same number -- the back end's and "version" in package.json --
 * and they disagreed: 2.0.0-SNAPSHOT on one side and 2.0.0 on the other, with nothing in either
 * build to notice. The page has never shown this number anyway; the badge in the rail is
 * whatever the server reports from /v2/version, which is the jar's own version and the only one
 * that can be wrong in a way anybody would care about.
 *
 * So package.json is derived now, from GF_VERSION:
 *
 *   1. the environment, which is what a release or a CI job sets
 *   2. .env beside this project, which is what a machine sets
 *   3. neither, and package.json is left exactly as it is
 *
 * Three rather than one because .env is not in the repository -- it is ignored, like every other
 * .env here -- so a fresh clone has to build without it rather than fail for the want of a
 * number nothing displays.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { setting } = require('./env.cjs');

const manifest = resolve(dirname(fileURLToPath(import.meta.url)), '../package.json');

const wanted = setting('GF_VERSION', '');
if (!wanted) {
  console.log('No GF_VERSION in the environment or .env; package.json keeps the version it has.');
  process.exit(0);
}

const source = readFileSync(manifest, 'utf8');
const current = JSON.parse(source).version;
if (current === wanted) {
  process.exit(0);
}

// rewritten as text rather than re-serialised: JSON.stringify would reformat the whole file and
// turn a one-line change into a diff nobody can read
writeFileSync(manifest, source.replace(/("version"\s*:\s*)"[^"]*"/, `$1"${wanted}"`));
console.log(`package.json version ${current} -> ${wanted}`);
