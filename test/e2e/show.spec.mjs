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

// Once the page has the whole feed, which its count of episodes says,
// Subscribe turns into Unsubscribe at once, under the second click
test('a double click on Subscribe subscribes once', async ({ page }) => {
  const main = page.getByRole('main');

  await page.goto(showPath);
  await expect(main.getByText(`${episodes.length} episodes.`, { exact: true })).toBeVisible();
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).dblclick();
  await expect(announcement(page)).toHaveText(`Subscribed to ${showTitle}`);
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
});

// HTML, section 10.2.4: a worker whose script fails to load fires error at
// its Worker in the page, and never runs
// https://html.spec.whatwg.org/multipage/workers.html#worker-processing-model
test('a worker that failed to load starts again at the next fetch', async ({ page, context }) => {
  const main = page.getByRole('main');
  let loads = 0;
  await context.route(url => url.pathname === '/js/worker.js', route =>
    ++loads === 1 ? route.abort() : route.continue());

  // the start of the feed comes without the worker, and the whole feed
  // through it
  await page.goto(showPath);
  await expect(main.getByText("The feed couldn't be fetched.")).toBeVisible();
  await expect(main.getByRole('link', { name: episodes[0].title })).toBeVisible();

  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(announcement(page)).toHaveText(`Subscribed to ${showTitle}`);
  expect(loads).toBe(2);
});

test('the Details come after the episodes on a phone, and beside them on a wider screen', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 500 });
  await page.goto(showPath);
  const details = page.getByRole('complementary', { name: 'Details' });

  await expect(details.locator('dt')).toHaveText(['Language', 'Latest episode', 'Schedule']);
  // the website and the feed are links of the line under the title, whose
  // text shows without the tooltip of the icon after it
  const byline = page.getByRole('main').locator('hgroup p');
  await expect(byline.getByRole('link')).toHaveText(['Website', 'Feed'], { useInnerText: true });
  await expect(details.locator('dt').first()).not.toBeInViewport();

  await page.setViewportSize({ width: 1280, height: 800 });
  await expect(details.locator('dt').first()).toBeInViewport();
});
