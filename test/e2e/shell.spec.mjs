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
  for (const title of ['Inbox', 'Library', 'Search', 'Queue']) {
    await navLink(page, title).click();
    await expect(navLink(page, title)).toHaveAttribute('aria-current', 'page');
    expect(await shell(page), title).toEqual(first);
  }
});
