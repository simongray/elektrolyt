// The performance budgets of the key flows, in Chrome at a quarter of the
// CPU's speed, one flow at a time, against the app that the e2e tests run
// on, e.g. a release with E2E_RELEASE=1.
//
//     npm run perf
//     PERF_CALIBRATE=1 npm run perf    write the budgets of five runs

import config from './playwright.config.mjs';

export default {
  ...config,
  testMatch: '*.perf.mjs',
  outputDir: '../../target/perf',
  fullyParallel: false,
  workers: 1,
  timeout: 600_000,
  projects: [{ name: 'chrome', use: { browserName: 'chromium', channel: 'chrome' } }],
};
