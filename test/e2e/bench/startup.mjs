// Times what the page's scripts cost to compile and evaluate until the
// first page paints, in headless Chrome with its CPU throttled, from V8's
// own events in a trace. It loads the page three times in one profile:
// cold, then warm and hot, since V8 keeps the code it compiled from the
// second load on. Run it against a release on a port of its own, e.g.
//
//   node test/e2e/bench/startup.mjs http://localhost:8411 4
//
// The evaluation of main.js includes the app's init, which it calls. With
// profile after the rate, it profiles the hot load instead, and prints the
// source files of the release's source maps that took the most time.
import { createRequire } from 'node:module';
import { chromium } from '@playwright/test';

const { SourceMapConsumer } = createRequire(import.meta.url)('source-map');
const [base = 'http://localhost:8400', rate = '4', mode] = process.argv.slice(2);

/** The milliseconds of each source file in the CPU `profile`, the most
    first, through the source maps of the scripts under /js/. */
async function bySource(profile) {
  const maps = {};
  const consumer = async url => {
    maps[url] ??= fetch(`${url}.map`).then(r => r.ok ? r.json() : null)
      .then(map => map && new SourceMapConsumer(map));
    return maps[url];
  };
  const self = new Map();
  profile.samples.forEach((id, i) => self.set(id, (self.get(id) ?? 0) + (profile.timeDeltas[i] ?? 0)));
  const totals = {};
  for (const { id, callFrame: { url, lineNumber, columnNumber } } of profile.nodes) {
    const us = self.get(id);
    if (!us) continue;
    let source = url ? new URL(url).pathname : '(native)';
    if (url.includes('/js/')) {
      const map = await consumer(url);
      source = map?.originalPositionFor({ line: lineNumber + 1, column: columnNumber }).source ?? source;
    }
    totals[source] = (totals[source] ?? 0) + us / 1000;
  }
  return Object.entries(totals).sort(([, a], [, b]) => b - a).slice(0, 20)
    .map(([source, ms]) => [source.replace(/^.*?(dk\/|cljs\/|goog\/|replicant\/|reitit\/|taoensso\/|com\/)/, '$1'), Math.round(ms)]);
}

/** The milliseconds of the trace `events` on the page's main thread, by
    script and by kind: compile, evaluate, and the use or making of V8's
    cache of code. */
function costs(events) {
  const main = events.find(e => e.name === 'thread_name' && e.args?.name === 'CrRendererMain');
  const kinds = {
    'v8.compile': 'compile', 'V8.CompileCode': 'compile',
    EvaluateScript: 'evaluate', 'v8.run': 'evaluate',
    'v8.consumeCache': 'cache', 'v8.produceCache': 'cache',
  };
  const out = {};
  for (const e of events) {
    const kind = kinds[e.name];
    const url = e.args?.data?.url ?? e.args?.fileName;
    if (!kind || !url || e.ph !== 'X' || (main && e.tid !== main.tid)) continue;
    const script = new URL(url).pathname;
    if (!script.startsWith('/js/')) continue;
    out[script] ??= {};
    out[script][kind] = Math.round(((out[script][kind] ?? 0) + e.dur / 1000) * 10) / 10;
  }
  return out;
}

const browser = await chromium.launch({ channel: 'chrome' });
try {
  const context = await browser.newContext();
  for (const run of ['cold', 'warm', 'hot']) {
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: Number(rate) });
    if (mode === 'profile' && run === 'hot') {
      await cdp.send('Profiler.enable');
      await cdp.send('Profiler.setSamplingInterval', { interval: 100 });
      await cdp.send('Profiler.start');
      await page.goto(`${base}/`);
      await page.waitForFunction(() => performance.getEntriesByName('startup/painted').length === 1,
                                 null, { timeout: 120_000 });
      const { profile } = await cdp.send('Profiler.stop');
      console.log(JSON.stringify({ run, rate: Number(rate), sources: await bySource(profile) }));
      await page.close();
      continue;
    }
    const events = [];
    cdp.on('Tracing.dataCollected', ({ value }) => events.push(...value));
    const complete = new Promise(resolve => cdp.once('Tracing.tracingComplete', resolve));
    await cdp.send('Tracing.start', {
      categories: 'devtools.timeline,v8,disabled-by-default-v8.compile',
      transferMode: 'ReportEvents',
    });
    await page.goto(`${base}/`);
    await page.waitForFunction(() => performance.getEntriesByName('startup/painted').length === 1,
                               null, { timeout: 120_000 });
    const painted = await page.evaluate(() => Math.round(performance.getEntriesByName('startup/painted')[0].startTime));
    await cdp.send('Tracing.end');
    await complete;
    console.log(JSON.stringify({ run, rate: Number(rate), painted, scripts: costs(events) }));
    await page.close();
  }
} finally {
  await browser.close();
}
