import { readFile } from 'node:fs/promises';
import { test, expect, feedUrl, navLink, showPath, showTitle } from './fixtures.mjs';

/** An OPML file of the show, as another podcast app exports it. */
const opml = `<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>From another app</title></head>
  <body><outline type="rss" text="${showTitle}" xmlUrl="${feedUrl}"/></body>
</opml>`;

/** Subscribe to the show from its page, and open Settings from the header. */
async function subscribeAndOpenSettings(page) {
  const main = page.getByRole('main');
  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
  await page.getByRole('banner').getByRole('link', { name: 'Settings' }).click();
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

test('the default of where new episodes go can apply to every show', async ({ page }) => {
  const main = page.getByRole('main');
  await subscribeAndOpenSettings(page);
  await main.getByLabel('New episodes of a new show go to').selectOption('Next in the queue');
  await main.getByRole('button', { name: 'Apply to all shows' }).click();
  await expect(main.getByRole('status').filter({ hasText: 'Applied' })).toHaveText('Applied to 1 show');

  await navLink(page, 'Library').click();
  await main.getByRole('link', { name: showTitle }).click();
  await expect(main.getByLabel('New episodes go to')).toHaveValue('next');
});

test('continuous play is a switch', async ({ page }) => {
  await page.goto('/settings');
  const continuous = page.getByRole('main').getByRole('switch', { name: 'Continuous play' });
  await expect(continuous).not.toBeChecked();
  await continuous.check();
  await expect(continuous).toBeChecked();
});
