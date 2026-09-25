/**
 * `npm start`, with the dev server's port taken from `.env` rather than from angular.json.
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
