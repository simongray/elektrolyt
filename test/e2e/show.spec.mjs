import { test, expect, announcement, episodes, feedUrl, showPath, showTitle }
  from './fixtures.mjs';

test('unsubscribing keeps the page by the URL of its feed', async ({ page }) => {
  const main = page.getByRole('main');
  const subscribe = main.getByRole('button', { name: `Subscribe to ${showTitle}` });
  const unsubscribe = main.getByRole('button', { name: `Unsubscribe from ${showTitle}` });
  const feedInAddress = url => url.searchParams.get('url') === feedUrl;

  await page.goto(showPath);
  await subscribe.click();
  await expect(unsubscribe).toBeVisible();
  await expect(page).not.toHaveURL(feedInAddress);

  await unsubscribe.click();
  await expect(announcement(page)).toHaveText(`Unsubscribed from ${showTitle}`);
  await expect(page).toHaveURL(feedInAddress);
  await expect(page.getByRole('heading', { level: 1, name: showTitle })).toBeVisible();
  await expect(main.getByRole('link', { name: episodes[0].title })).toBeVisible();

  await subscribe.click();
  await expect(announcement(page)).toHaveText(`Subscribed to ${showTitle}`);
  await expect(unsubscribe).toBeVisible();
  await expect(page).not.toHaveURL(feedInAddress);
});
