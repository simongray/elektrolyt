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
