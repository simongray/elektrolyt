// Times the app with a library of 50 shows of podcast-clj's sweep corpus,
// 27,575 episodes in all, in headless Chrome with a profile kept in
// target/bench. The proxy's requests for the shows' feeds get the corpus's
// files, and any other is refused, so that a run is the same each time.
//
//   node test/e2e/bench/bench.mjs seed           import shows.opml into a new profile
//   node test/e2e/bench/bench.mjs open 5         open Home 5 times
//   node test/e2e/bench/bench.mjs subscribe 3    subscribe to the first show 3 times
//   node test/e2e/bench/bench.mjs colours 5      time the frames as the Library's
//                                                colours change 5 times, with CPU
//                                                throttling, or a show's with show
//
// BENCH_URL is the app, http://localhost:8400 by default, and BENCH_CORPUS
// the folder of the corpus's feeds, ../podcast-clj/dev-resources/corpus/sweep
// by default.
import { readFileSync, rmSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const here = new URL('.', import.meta.url);
const root = new URL('../../../', here);
const base = process.env.BENCH_URL ?? 'http://localhost:8400';
const corpus = process.env.BENCH_CORPUS
  ?? fileURLToPath(new URL('../podcast-clj/dev-resources/corpus/sweep/', root));
const profile = fileURLToPath(new URL('target/bench/profile', root));
const opml = readFileSync(new URL('shows.opml', here));
const files = JSON.parse(readFileSync(new URL('feeds.json', here), 'utf8'));
const [mode, n = '3'] = process.argv.slice(2);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

/** The answer to the proxy's `request` for the feed at `url`: the corpus's
    file, or the part of it that a Range header asks for. */
function feed(route, url) {
  const file = files[url];
  if (!file) return route.fulfill({ status: 404 });
  const body = readFileSync(`${corpus}/${file}`);
  const range = /^bytes=(\d+)-(\d*)$/.exec(route.request().headers().range ?? '');
  if (!range) return route.fulfill({ contentType: 'application/rss+xml', body });
  const start = Number(range[1]);
  const end = range[2] ? Math.min(Number(range[2]), body.length - 1) : body.length - 1;
  return route.fulfill({
    status: 206,
    contentType: 'application/rss+xml',
    headers: { 'Content-Range': `bytes ${start}-${end}/${body.length}` },
    body: body.subarray(start, end + 1),
  });
}

// when the page's heading first appears, when main is no longer busy, and
// each long task, from the start
function measure() {
  window.__bench = { long: [] };
  new PerformanceObserver(list => {
    for (const e of list.getEntries()) window.__bench.long.push([Math.round(e.startTime), Math.round(e.duration)]);
  }).observe({ type: 'longtask' });
  new MutationObserver((_, observer) => {
    const main = document.querySelector('main');
    if (main?.querySelector('h1') && !window.__bench.frame) window.__bench.frame = Math.round(performance.now());
    if (main?.querySelector('h1') && !main.hasAttribute('aria-busy')) {
      window.__bench.page = Math.round(performance.now());
      observer.disconnect();
    }
  }).observe(document, { childList: true, subtree: true, attributes: true });
}

const tasks = long => ({
  longest: Math.max(0, ...long.map(([, d]) => d)),
  total: long.reduce((sum, [, d]) => sum + d, 0),
});

/** The milliseconds of each frame for 800 ms after the shell's seeds change,
    with their transition when `moving`, or at once otherwise. */
async function fade({ moving, i }) {
  const shell = document.querySelector('.shell');
  const seeds = ['oklch(55% 0.15 30)', 'oklch(45% 0.12 250)'];
  const frame = () => new Promise(resolve => requestAnimationFrame(resolve));
  shell.style.transition = moving ? '' : 'none';
  let last = await frame();
  const end = last + 800;
  shell.style.setProperty('--seed', seeds[i % 2]);
  shell.style.setProperty('--bar-seed', seeds[(i + 1) % 2]);
  const frames = [];
  const seen = new Set();
  while (last < end) {
    const now = await frame();
    frames.push(now - last);
    seen.add(getComputedStyle(shell).getPropertyValue('--seed'));
    last = now;
  }
  shell.style.transition = '';
  await new Promise(resolve => setTimeout(resolve, 400));
  return { frames, colours: seen.size, elements: document.querySelectorAll('.shell *').length };
}

/** Whether the refresh in the page's store has its result. */
const refreshed = () => {
  const c = window.cljs.core;
  const status = c.get(c.deref(window.dk.simongray.elektrolyt.store), c.keyword('refresh-status'));
  return Boolean(status && !c.get(status, c.keyword('running?')) && c.get(status, c.keyword('result')));
};

if (mode === 'seed') rmSync(profile, { recursive: true, force: true });
const context = await chromium.launchPersistentContext(profile, {
  channel: 'chrome', headless: true, viewport: { width: 1280, height: 800 },
});
// a browser killed while it writes can lose the profile's IndexedDB, so it's
// closed whatever happens
try {
  await context.route(url => url.pathname === '/proxy',
    route => feed(route, new URL(route.request().url()).searchParams.get('url')));
  await context.route(url => url.pathname === '/artwork', route => route.fulfill({ status: 404 }));
  await context.route(url => url.origin !== new URL(base).origin, route => route.abort());
  await context.addInitScript(measure);

  if (mode === 'seed') {
    const page = await context.newPage();
    const main = page.getByRole('main');
    await page.goto(`${base}/settings`);
    await page.waitForFunction(() => !document.querySelector('main')?.hasAttribute('aria-busy'));
    const t0 = await page.evaluate(() => performance.now());
    await main.getByLabel('Import from an OPML file').setInputFiles({ name: 'shows.opml', mimeType: 'text/x-opml', buffer: opml });
    await page.waitForFunction(refreshed, null, { timeout: 600_000, polling: 500 });
    const t1 = await page.evaluate(() => performance.now());
    await sleep(10_000);
    const long = await page.evaluate(t => window.__bench.long.filter(([s]) => s >= t), t0);
    await page.goto(`${base}/library`);
    await main.locator('.library-shows > li').first().waitFor({ timeout: 120_000 });
    const shows = await main.locator('.library-shows > li').count();
    console.log(JSON.stringify({ shows, importSeconds: Math.round((t1 - t0) / 100) / 10, ...tasks(long) }));
  } else if (mode === 'open') {
    for (let i = 1; i <= Number(n); i++) {
      const page = await context.newPage();
      await page.goto(`${base}/`);
      await page.waitForFunction(() => window.__bench.page, null, { timeout: 120_000 });
      await sleep(3000);
      const { frame, page: shown, long } = await page.evaluate(() => window.__bench);
      console.log(JSON.stringify({ run: i, frame, page: shown, ...tasks(long.filter(([s]) => s <= shown + 3000)) }));
      await page.close();
    }
  } else if (mode === 'subscribe') {
    const title = /text="([^"]*)"/.exec(opml.toString())[1].replaceAll('&amp;', '&');
    const page = await context.newPage();
    const main = page.getByRole('main');
    await page.goto(`${base}/library`);
    await main.getByRole('link', { name: title, exact: true }).click();
    const subscribe = main.getByRole('button', { name: `Subscribe to ${title}` });
    const unsubscribe = main.getByRole('button', { name: `Unsubscribe from ${title}` });
    await unsubscribe.waitFor({ timeout: 120_000 });
    for (let i = 1; i <= Number(n); i++) {
      await unsubscribe.click();
      await subscribe.waitFor();
      // the whole feed, before the click
      await main.getByText(/^\d+ episodes?\.$/).waitFor({ timeout: 120_000 });
      await sleep(4000);
      const t0 = await page.evaluate(() => performance.now());
      await subscribe.click();
      await unsubscribe.waitFor({ timeout: 120_000 });
      await sleep(8000);
      const long = await page.evaluate(t => window.__bench.long.filter(([s]) => s >= t), t0);
      console.log(JSON.stringify({ run: i, show: title, ...tasks(long) }));
    }
  } else if (mode === 'colours') {
    const page = await context.newPage();
    await page.goto(`${base}/library`);
    await page.waitForFunction(() => window.__bench.page, null, { timeout: 120_000 });
    // the show with the most episodes, whose page lists the most rows
    if (process.argv[4] === 'show') {
      const title = /text="([^"]*)"/.exec(opml.toString())[1].replaceAll('&amp;', '&');
      await page.getByRole('main').getByRole('link', { name: title, exact: true }).click();
      await page.getByRole('main').getByText(/^\d+ episodes?\.$/).waitFor({ timeout: 120_000 });
    }
    await sleep(5000);
    const cdp = await context.newCDPSession(page);
    for (const rate of [1, 4, 6]) {
      await cdp.send('Emulation.setCPUThrottlingRate', { rate });
      for (const moving of [true, false]) {
        const frames = [];
        let colours = 0;
        let elements = 0;
        for (let i = 0; i < Number(n); i++) {
          const run = await page.evaluate(fade, { moving, i });
          frames.push(...run.frames);
          colours += run.colours;
          elements = run.elements;
        }
        console.log(JSON.stringify({
          rate, moving, elements, colours, frames: frames.length,
          longest: Math.round(Math.max(...frames)),
          over20: frames.filter(ms => ms > 20).length,
          mean: Math.round(frames.reduce((a, b) => a + b, 0) / frames.length * 10) / 10,
        }));
      }
    }
  } else {
    console.log('A mode: seed, open, subscribe or colours');
  }
} finally {
  await context.close();
}
