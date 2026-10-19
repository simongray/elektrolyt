// The end-to-end tests, in Firefox and in your installed Chrome, against
// the app's own server on a free port. The server serves the app that the
// watch built into public/.
//
//     npm run e2e

import { createServer } from 'node:net';
import { fileURLToPath } from 'node:url';
import { defineConfig } from '@playwright/test';

/** A promise of a port on localhost that nothing listens on. */
function freePort() {
  return new Promise((resolve, reject) => {
    const server = createServer().once('error', reject);
    server.listen(0, 'localhost', () => {
      const { port } = server.address();
      server.close(() => resolve(port));
    });
  });
}

// Each worker loads this file again, so the runner picks the port once and
// the workers inherit it with the rest of its environment.
process.env.E2E_PORT ||= String(await freePort());

const port = process.env.E2E_PORT;

// without the secrets of the Podcast Index, which no test searches
const start = '((requiring-resolve \'dk.simongray.podcast-app.server/start!)'
  + ` {:port ${port} :secrets nil})`;

// the libraries from the checkouts beside this one, like the watch's,
// unless E2E_ALIASES names other aliases, e.g. ':server' on GitHub
const aliases = process.env.E2E_ALIASES ?? ':server:local';

export default defineConfig({
  testMatch: '*.spec.mjs',
  outputDir: '../../target/e2e',
  reporter: 'list',
  fullyParallel: true,
  use: {
    baseURL: `http://localhost:${port}`,
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'firefox', use: { browserName: 'firefox' } },
    { name: 'chrome', use: { browserName: 'chromium', channel: 'chrome' } },
  ],
  webServer: {
    command: `clojure -M${aliases} -e "${start}"`,
    cwd: fileURLToPath(new URL('../..', import.meta.url)),
    url: `http://localhost:${port}/api/server`,
    timeout: 120_000,
  },
});
