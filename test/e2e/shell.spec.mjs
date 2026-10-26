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
