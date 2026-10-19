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

test('a link leads past the episodes to the Details on a phone, and not where they are beside them', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 500 });
  await page.goto(showPath);
  const details = page.getByRole('complementary', { name: 'Details' });
  const heading = details.getByRole('heading', { level: 2, name: 'Details' });

  await expect(details.locator('dt')).toHaveText(
    ['Website', 'Language', 'Latest episode', 'Schedule', 'Feed']);
  await expect(details.locator('dd').first()).toHaveText('show.example');
  await expect(heading).not.toBeInViewport();

  const link = page.getByRole('main').getByRole('link', { name: 'Details' });
  await link.click();
  await expect(heading).toBeFocused();
  await expect(details.locator('dt').first()).toBeInViewport();

  await page.setViewportSize({ width: 1280, height: 800 });
  await expect(details).toBeInViewport();
  await expect(link).toBeHidden();
});
