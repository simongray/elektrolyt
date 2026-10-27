// The shell around every page: the header, the navigation and the player.

import { test, expect, navLink } from './fixtures.mjs';

/** Where the main content starts, and how wide the rail is, on the `page`. */
function shell(page) {
  return page.evaluate(() => [
    document.querySelector('main').getBoundingClientRect().left,
    document.querySelector('.shell > nav').getBoundingClientRect().width,
  ]);
}

test('the rail keeps its width from page to page, so the content stays put', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/');
  // the app renders the shell after the page has loaded, which on a slow
  // runner comes after the font
  await expect(page.getByRole('main')).toBeVisible();
  // Charis, in place of the Charter that stands in while it loads, whose
  // small caps the browser makes up narrower; fonts.ready alone can come
  // before Firefox asks for it
  await page.evaluate(() => document.fonts.load('1rem Charis').then(() => document.fonts.ready));
  const first = await shell(page);
  for (const title of ['Queue', 'Library', 'Search', 'Home']) {
    await navLink(page, title).click();
    await expect(navLink(page, title)).toHaveAttribute('aria-current', 'page');
    expect(await shell(page), title).toEqual(first);
  }
});

test('in a narrow window, the rail shows icons alone, and their titles name them', async ({ page }) => {
  await page.setViewportSize({ width: 700, height: 700 });
  await page.goto('/queue');
  const library = navLink(page, 'Library');
  await expect(library.locator('svg')).toBeVisible();
  expect((await library.locator('.title').boundingBox()).width, 'the title, for a screen reader')
    .toBeLessThanOrEqual(1);
  const [, rail] = await shell(page);
  expect(rail, 'a rail as narrow as its icons').toBeLessThan(80);
});

test('on a phone, a tab shows its icon alone, and its title names it', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 });
  await page.goto('/queue');
  const library = navLink(page, 'Library');
  await expect(library.locator('svg')).toBeVisible();
  expect((await library.locator('.title').boundingBox()).width, 'the title, for a screen reader')
    .toBeLessThanOrEqual(1);
  await library.click();
  await expect(library).toHaveAttribute('aria-current', 'page');
});

// Safari repaints all of a scroller with a background of its own that
// scrolls with the content, at every step of a scroll (look.css)
test('the grain lies on a paper inside main, as tall as main on a short page', async ({ page }) => {
  await page.goto('/queue');
  await expect(page.getByRole('heading', { name: 'Queue', level: 1 })).toBeVisible();
  const [main, paper, mainHeight, paperHeight] = await page.evaluate(() => {
    const main = document.querySelector('main');
    const paper = main.querySelector(':scope > .paper');
    return [getComputedStyle(main).backgroundImage, getComputedStyle(paper).backgroundImage,
            main.clientHeight, paper.offsetHeight];
  });
  expect(main, 'main without a background').toBe('none');
  expect(paper).toContain('grain');
  expect(paperHeight).toBeGreaterThanOrEqual(mainHeight);
});

test('the frame of the page shows while the app starts, and a tap in it opens that page', async ({ page }) => {
  // what the server offers comes late, so the app starts slowly
  let release;
  const held = new Promise(resolve => { release = resolve; });
  await page.route('**/api/server', async route => {
    await held;
    await route.fallback();
  });
  await page.goto('/library');
  const main = page.getByRole('main');
  await expect(main).toHaveAttribute('aria-busy', 'true');
  await expect(main.getByRole('heading', { level: 1, name: 'Library' })).toBeVisible();
  await expect(navLink(page, 'Library')).toHaveAttribute('aria-current', 'page');
  await expect(page).toHaveTitle('Library – Elektrolyt');

  await page.evaluate(() => { window.loadedOnce = true; });
  await navLink(page, 'Queue').click();
  await expect(page).toHaveURL(/\/queue$/);
  await expect(main.getByRole('heading', { level: 1, name: 'Queue' })).toBeVisible();
  release();
  await expect(main).not.toHaveAttribute('aria-busy');
  await expect(main.getByRole('heading', { level: 1, name: 'Queue' })).toBeVisible();
  expect(await page.evaluate(() => window.loadedOnce), 'without loading the page again').toBe(true);
});

test('the bold of the headings and the buttons comes with the page, before they render', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('main').getByRole('heading', { level: 1 })).toBeVisible();
  // a preloaded font's FontFace still loads after the render, so the check
  // is of when the file was asked for, rather than of document.fonts: before
  // the app's script arrived, which no render can come before. Firefox names
  // a preload's initiator other rather than link.
  const started = name => page.evaluate(n => performance.getEntriesByType('resource')
    .find(r => r.name.endsWith(n)), name);
  await expect.poll(async () => (await started('/Charis-Bold.woff2'))?.startTime).toBeDefined();
  const bold = await started('/Charis-Bold.woff2');
  const script = await started('/js/main.js');
  expect(bold.startTime).toBeLessThan(script.responseEnd);
});

// the marks that the app makes of its start, which a profile of any build
// finds on the page's timeline
test('the start of the page marks each of its stages, in order', async ({ page }) => {
  await page.goto('/');
  await expect.poll(() => page.evaluate(() => performance.getEntriesByName('startup/painted').length)).toBe(1);
  const at = await page.evaluate(() => Object.fromEntries(
    performance.getEntriesByType('mark')
      .filter(m => m.name.startsWith('startup/'))
      .map(m => [m.name.slice('startup/'.length), m.startTime])));

  for (const [before, after] of [['init', 'frame'], ['frame', 'library-reading'],
                                 ['library-reading', 'library-read'], ['library-read', 'library'],
                                 ['library', 'colors'], ['server', 'colors'],
                                 ['colors', 'page'], ['page', 'painted']]) {
    expect(at[before], `${before} before ${after}`).toBeLessThanOrEqual(at[after]);
  }
});
