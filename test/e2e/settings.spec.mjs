import { readFile } from 'node:fs/promises';
import { test, expect, episodePath, episodes, feedUrl, navLink, showPath, showTitle } from './fixtures.mjs';

/** An OPML file of the show, as another podcast app exports it. */
const opml = `<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>From another app</title></head>
  <body><outline type="rss" text="${showTitle}" xmlUrl="${feedUrl}"/></body>
</opml>`;

/** Subscribe to the show from its page, and open Settings from the navigation. */
async function subscribeAndOpenSettings(page) {
  const main = page.getByRole('main');
  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
  await navLink(page, 'Settings').click();
}

test('importing an OPML file subscribes to its shows', async ({ page }) => {
  const main = page.getByRole('main');
  await page.goto('/settings');
  await main.getByLabel('Import from an OPML file').setInputFiles({
    name: 'shows.opml',
    mimeType: 'text/x-opml',
    buffer: Buffer.from(opml),
  });
  await expect(main.getByRole('status').filter({ hasText: 'Subscribed' }))
    .toHaveText('Subscribed to 1 show from the file. Their episodes are on the way.');

  await navLink(page, 'Library').click();
  await expect(main.getByRole('heading', { name: showTitle })).toBeVisible();
});

test('exporting downloads the subscriptions as OPML', async ({ page }) => {
  await subscribeAndOpenSettings(page);
  const download = page.waitForEvent('download');
  await page.getByRole('main').getByRole('button', { name: 'Export as OPML' }).click();
  const file = await download;
  expect(file.suggestedFilename()).toBe('subscriptions.opml');
  expect(await readFile(await file.path(), 'utf8')).toContain(feedUrl);
});

test('continuous play is a switch', async ({ page }) => {
  await page.goto('/settings');
  const continuous = page.getByRole('main').getByRole('switch', { name: 'Continuous play' });
  await expect(continuous).not.toBeChecked();
  await continuous.check();
  await expect(continuous).toBeChecked();
});

/** The distance of the `heading` from the top of the main content, inside its edge, and how far the whole page scrolled. */
function landing(heading) {
  return heading.evaluate(async h => {
    // once the faces that the page needs are in, which move the text
    await document.fonts.ready;
    const main = h.closest('main');
    return {
      below: h.getBoundingClientRect().top - (main.getBoundingClientRect().top + main.clientTop),
      scrolled: window.scrollY,
    };
  });
}

test('a link to a group of the settings shows it at the top, from another page and on a reload', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 });
  const main = page.getByRole('main');
  const heading = main.getByRole('heading', { name: 'Search', level: 2 });
  await page.goto('/search');
  await main.getByRole('link', { name: 'Change the directories' }).click();
  await expect(page).toHaveURL(/\/settings#search$/);
  await expect(heading).toBeFocused();
  const shown = await landing(heading);
  expect(Math.abs(shown.below)).toBeLessThan(1);
  expect(shown.scrolled, 'the main content scrolled, but not the page').toBe(0);

  await page.reload();
  await expect(heading).toBeFocused();
  expect(Math.abs((await landing(heading)).below)).toBeLessThan(1);
});

/** The colour scheme that the page of `page` is in. */
function scheme(page) {
  return page.evaluate(() => getComputedStyle(document.documentElement).colorScheme);
}

test("the colours are the device's, or light or dark as the listener chooses, in the profile", async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/settings');
  const colours = page.getByRole('main').getByLabel('Light or dark');
  await expect(colours).toHaveValue('auto');
  expect(await scheme(page)).toBe('light dark');

  await colours.selectOption('Dark');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  expect(await scheme(page), 'dark on a light device').toBe('dark');

  // dark from the first paint, before the app has restored the library
  await page.reload({ waitUntil: 'domcontentloaded' });
  expect(await page.evaluate(() => document.documentElement.dataset.theme)).toBe('dark');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('main').getByLabel('Light or dark').selectOption('Automatic, as the device');
  await expect(page.locator('html')).not.toHaveAttribute('data-theme');
  expect(await page.evaluate(() => localStorage.getItem('dk.simongray.elektrolyt.theme'))).toBeNull();
});

/** The colour of the link to the Queue in the navigation of `page`. */
function linkColour(page) {
  return navLink(page, 'Queue').evaluate(a => getComputedStyle(a).color);
}

test('more contrast follows the device, or the listener, and darkens the labels', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light', contrast: 'no-preference' });
  await page.goto('/settings');
  // the frame as the app starts has a navigation of its own, which goes
  await expect(page.getByRole('main')).not.toHaveAttribute('aria-busy', 'true');
  const standard = await linkColour(page);

  // before a reload, since Firefox's emulation doesn't restyle a page that
  // has loaded
  await page.emulateMedia({ colorScheme: 'light', contrast: 'more' });
  await page.reload();
  await expect.poll(() => linkColour(page), 'as the device asks').not.toBe(standard);

  await page.getByRole('main').getByLabel('Contrast').selectOption('Standard');
  await expect(page.locator('html')).toHaveAttribute('data-contrast', 'standard');
  await expect.poll(() => linkColour(page), 'unless the listener chose the standard').toBe(standard);

  await page.reload({ waitUntil: 'domcontentloaded' });
  expect(await page.evaluate(() => document.documentElement.dataset.contrast), 'from the first paint').toBe('standard');
});

// colors_test has what the switch does to the colours, since the show of
// these tests has no artwork
test('the colours from the artwork are a switch that the profile keeps', async ({ page }) => {
  await page.goto('/settings');
  const colours = page.getByRole('main').getByRole('switch', { name: 'Colours from the artwork' });
  await expect(colours).toBeChecked();
  await colours.uncheck();
  await expect(colours).not.toBeChecked();
  await page.reload();
  await expect(colours).not.toBeChecked();
});

/** Whether the links to the Queue and the Library in the navigation of `page` have the same colour. */
async function labelsAlike(page) {
  const [queue, library] = await Promise.all(['Queue', 'Library'].map(t => navLink(page, t).evaluate(a => getComputedStyle(a).color)));
  return queue === library;
}

test("the navigation's colours are a page's, the player's, or none, which leaves its labels in the same ink", async ({ page }) => {
  await page.goto('/settings');
  // the frame as the app starts has a navigation of its own, which goes
  await expect(page.getByRole('main')).not.toHaveAttribute('aria-busy', 'true');
  expect(await labelsAlike(page), 'a colour for each page').toBe(false);
  const colours = page.getByRole('main').getByLabel('Colours in the navigation');
  await colours.selectOption("The player's colour");
  await expect(page.locator('.shell')).toHaveAttribute('data-palette', 'player');
  await expect.poll(() => labelsAlike(page), "or the player's colour, the same for every page").toBe(true);
  await colours.selectOption('Colours for colour blindness');
  await expect(page.locator('.shell')).toHaveAttribute('data-palette', 'color-blind');
  expect(await labelsAlike(page), 'or one for colour blindness').toBe(false);
  await colours.selectOption('None');
  await expect.poll(() => labelsAlike(page)).toBe(true);
});

test('the lock screen follows the choice of what next and previous go to', async ({ page }) => {
  // the handlers that the page gives the lock screen, the last of each
  await page.addInitScript(() => {
    window.handlers = {};
    const session = navigator.mediaSession;
    if (!session) return;
    const set = session.setActionHandler.bind(session);
    session.setActionHandler = (action, handler) => {
      window.handlers[action] = Boolean(handler);
      return set(action, handler);
    };
  });
  await page.goto(episodePath(episodes[0]));
  await page.getByRole('main').getByRole('button', { name: 'Play', exact: true }).click();
  const next = () => page.evaluate(() => window.handlers.nexttrack ?? false);
  await expect.poll(() => page.evaluate(() => Object.keys(window.handlers).length)).toBeGreaterThan(0);
  expect(await next(), 'none for an episode without chapters').toBe(false);

  await navLink(page, 'Settings').click();
  await page.getByRole('main').getByLabel('Next and previous on the lock screen go to')
    .selectOption({ label: 'The episodes of the queue' });
  await expect.poll(next, 'once they go to the queue, while the episode plays').toBe(true);
});
