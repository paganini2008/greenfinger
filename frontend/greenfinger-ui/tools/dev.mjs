/**
 * `npm start`, with the dev server's port taken from `.env`.
 *
 * angular.json no longer names a port at all: two places that can disagree about which port
 * this is are worse than one. `ng serve` on its own therefore takes Angular's 4200; this is
 * the supported way in, and it is what package.json's `start` runs.
 *
 * The port is a property of the machine -- 9700 is free here and is somebody else's repository
 * manager elsewhere -- which is what `.env` is for. angular.json cannot read it, so the flag is
 * passed on the command line, where it beats the file's own value.
 */
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';

const { setting } = createRequire(import.meta.url)('./env.cjs');

const port = setting('GF_DEV_PORT', '9700');
const child = spawn('ng', ['serve', '--port', port, ...process.argv.slice(2)], {
  stdio: 'inherit',
  shell: true,
});
child.on('exit', (code) => process.exit(code ?? 0));
