// The budgets of the key flows, which keep a freeze from coming back: a
// show of 2,500 episodes opened, subscribed to, left for the Library and
// back, shown in full, played, and checked for new episodes, in Chrome at a
// quarter of the CPU's speed.
//
// A flow's longest frame, the longest gap between animation frames, must
// stay under its budget in perf-budgets.json: three times the median of
// five runs, which PERF_CALIBRATE=1 measures and writes, and at least
// 100 ms, so that a pause for garbage doesn't fail a flow that's quick
// otherwise. The time from a
// click on Subscribe to its next paint must stay under 200 ms, which the
// app's paint before the work gives. The budgets come from the machine
// that calibrated them, so a slower one may need its own.

import { readFileSync, writeFileSync } from 'node:fs';
import { test, expect, announcement, episodePath, navLink, playsPast, showPath, showTitle }
  from './fixtures.mjs';

const budgetsFile = new URL('perf-budgets.json', import.meta.url);
const calibrating = Boolean(process.env.PERF_CALIBRATE);
const runs = calibrating ? 5 : 1;
const budgets = calibrating ? {} : JSON.parse(readFileSync(budgetsFile, 'utf8'));

/** The `n` episodes of a long show, a day apart, oldest first. */
function many(n) {
  return Array.from({ length: n }, (_, i) => ({
    guid: `perf-episode-${i + 1}`,
    title: `Episode ${i + 1}`,
    published: new Date(Date.UTC(2019, 0, 1) + i * 86_400_000).toISOString(),
  }));
}

// In the page from its start: the gaps between animation frames, the
// interactions slower than 16 ms by Event Timing, and in Chrome the long
// animation frames, until reset starts them over
function record() {
  let last = performance.now();
  const perf = window.__perf = {
    gaps: [], events: [], loafs: [],
    reset() {
      perf.gaps = [];
      perf.events = [];
      perf.loafs = [];
      last = performance.now();
    },
  };
  const frame = now => {
    perf.gaps.push(now - last);
    last = now;
    requestAnimationFrame(frame);
  };
  requestAnimationFrame(frame);
  new PerformanceObserver(list => {
    for (const e of list.getEntries()) perf.events.push([e.name, e.duration]);
  }).observe({ type: 'event', durationThreshold: 16 });
  if (PerformanceObserver.supportedEntryTypes.includes('long-animation-frame')) {
    new PerformanceObserver(list => {
      for (const e of list.getEntries()) perf.loafs.push(e.duration);
    }).observe({ type: 'long-animation-frame' });
  }
}

/** What the page recorded since its start or the last reset. */
function measured(page) {
  return page.evaluate(() => {
    const { gaps, events, loafs } = window.__perf;
    const longest = xs => Math.round(Math.max(0, ...xs));
    return {
      longestFrame: longest(gaps),
      framesOver50: gaps.filter(gap => gap > 50).length,
      longestLoaf: longest(loafs),
      click: longest(events.filter(([name]) => ['pointerdown', 'pointerup', 'click'].includes(name))
        .map(([, duration]) => duration)),
    };
  });
}

const reset = page => page.evaluate(() => window.__perf.reset());

// once a flow's condition holds, the work it set off ends within this
const settle = page => page.waitForTimeout(2_000);

const results = {};

/** Record what the flow `name` measured, and check its budget. */
function check(name, result) {
  (results[name] ??= []).push(result);
  test.info().annotations.push({ type: name, description: JSON.stringify(result) });
  console.log(`${name}: ${JSON.stringify(result)}`);
  if (!calibrating && budgets[name]) {
    expect.soft(result.longestFrame, `the longest frame of ${name}`).toBeLessThan(budgets[name]);
  }
}

for (let run = 1; run <= runs; run++) {
  test(`the key flows stay within their budgets${calibrating ? `, run ${run}` : ''}`, async ({ page, show }) => {
    show.episodes = many(2_500);
    await page.addInitScript(record);
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    const main = page.getByRole('main');
    const whole = main.getByText(/^[\d,]+ episodes\.$/);

    await page.goto(showPath);
    await whole.waitFor({ timeout: 120_000 });
    await settle(page);
    check('show page', await measured(page));

    await reset(page);
    await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
    await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` }))
      .toBeVisible({ timeout: 60_000 });
    await settle(page);
    const subscribed = await measured(page);
    check('subscribe', subscribed);
    expect.soft(subscribed.click, 'from a click on Subscribe to its paint').toBeLessThan(200);

    await reset(page);
    for (let i = 0; i < 2; i++) {
      await navLink(page, 'Library').click();
      await main.getByRole('link', { name: showTitle, exact: true }).click();
      await whole.waitFor({ timeout: 60_000 });
    }
    await settle(page);
    check('library and back', await measured(page));

    await reset(page);
    await main.getByRole('button', { name: 'Show more episodes' }).click();
    await settle(page);
    check('show more', await measured(page));

    await page.goto(episodePath(show.episodes.at(-1)));
    await main.getByRole('button', { name: 'Play', exact: true }).waitFor({ timeout: 60_000 });
    await settle(page);
    await reset(page);
    await main.getByRole('button', { name: 'Play', exact: true }).click();
    await playsPast(page, 2);
    await settle(page);
    check('play', await measured(page));

    await navLink(page, 'Library').click();
    await main.getByRole('button', { name: 'Check for new episodes' }).waitFor();
    await settle(page);
    await reset(page);
    await main.getByRole('button', { name: 'Check for new episodes' }).click();
    await expect(announcement(page)).toHaveText('No new episodes', { timeout: 60_000 });
    await settle(page);
    check('check for new episodes', await measured(page));
  });
}

test.afterAll(() => {
  if (!calibrating) return;
  const median = xs => xs.toSorted((a, b) => a - b)[Math.floor(xs.length / 2)];
  const written = Object.fromEntries(Object.entries(results).map(([name, rs]) =>
    [name, Math.max(100, Math.ceil(3 * median(rs.map(r => r.longestFrame)) / 10) * 10)]));
  writeFileSync(budgetsFile, `${JSON.stringify(written, null, 2)}\n`);
  console.log(`budgets: ${JSON.stringify(written)}`);
});
