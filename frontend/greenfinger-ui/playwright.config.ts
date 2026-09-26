import { defineConfig, devices } from '@playwright/test';

// require rather than import: playwright loads this file as CommonJS, and env.cjs is the one
// reader of .env that the dev server and the version sync also use
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { setting } = require('./tools/env.cjs') as { setting: (k: string, d: string) => string };

/**
 * End to end against the real thing: the packaged server, serving both the page and the api from
 * one process, exactly as a deployment does.
 *
 * There is no webServer entry on purpose. Starting the jar from here would mean this config also
 * owning a database, a crawl directory and a port, and a failure in any of that would be reported
 * as a failing test. Start the server, then run these.
 *
 * Where to find it comes from `.env` like every other setting in this project -- `GF_E2E_URL`, or
 * the dev server's own port when that is not set. See tools/env.cjs.
 */
const baseURL = setting('GF_E2E_URL', `http://localhost:${setting('GF_DEV_PORT', '9700')}`);

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  // makes the catalog the read-only specs need, if the server has none
  globalSetup: './e2e/global-setup.ts',
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
