import { defineConfig, devices } from '@playwright/test';

/**
 * End to end against the real thing: the packaged server, serving both the page and the api from
 * one process, exactly as a deployment does.
 *
 * There is no webServer entry on purpose. Starting the jar from here would mean this config also
 * owning a database, a crawl directory and a port, and a failure in any of that would be reported
 * as a failing test. Start the server, point GF_E2E_URL at it, run these.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  use: {
    baseURL: process.env['GF_E2E_URL'] ?? 'http://localhost:8088',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
