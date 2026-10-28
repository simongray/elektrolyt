// The end-to-end tests, in Firefox and in your installed Chrome, against
// the app's own server on a free port. The server serves the app that the
// watch built into public/, or with E2E_RELEASE=1, the release that
// user/release! built into target/release/.
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
const release = process.env.E2E_RELEASE
  ? ' :client-dir \\"target/release\\" :release? true'
  : '';
const start = '((requiring-resolve \'dk.simongray.elektrolyt.server/start!)'
  + ` {:port ${port} :secrets nil${release}})`;

// the libraries from the checkouts beside this one, like the watch's,
// unless E2E_ALIASES names other aliases, e.g. ':server' on GitHub
const aliases = process.env.E2E_ALIASES ?? ':server:local';

export default defineConfig({
  testMatch: '*.spec.mjs',
  outputDir: '../../target/e2e',
  // In CI, a test that fails runs once more, so that a flake in its timing
  // doesn't fail the run. Playwright still counts it as flaky, and the github
  // reporter shows the error of its failed attempt on the run's page.
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['github']] : 'list',
  fullyParallel: true,
  use: {
    baseURL: `http://localhost:${port}`,
    trace: 'retain-on-failure',
    // so that a test measures what's still, and a test of a motion asks for
    // no-preference itself; motion_test checks that the CSS gates each
    reducedMotion: 'reduce',
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
