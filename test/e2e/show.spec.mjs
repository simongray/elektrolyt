import { test, expect, announcement, episodePath, episodes, feedUrl, navLink, showPath, showTitle }
  from './fixtures.mjs';

// The worker keeps only the fullest of an episode's notes, which the page
// shows, and so a refresh of the same feed finds nothing changed
test('an episode shows its fullest notes, which a refresh finds unchanged', async ({ page }) => {
  const main = page.getByRole('main');
  const changed = () => page.evaluate(() => {
    const c = window.cljs.core;
    const k = c.keyword;
    const status = c.get(c.deref(window.dk.simongray.elektrolyt.store), k('refresh-status'));
    return c.get(c.get(c.get(status, k('result')), k('counts')), k('changed'));
  });

  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
  await page.goto(episodePath(episodes[0]));
  await expect(main.getByText(`The notes of ${episodes[0].title}, in full.`)).toBeVisible();
  await expect(main.getByText(`A summary of ${episodes[0].title}.`)).toHaveCount(0);

  await navLink(page, 'Library').click();
  await main.getByRole('button', { name: 'Check for new episodes' }).click();
  await expect(announcement(page)).toHaveText('No new episodes');
  expect(await changed()).toBe(0);
});

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

// After a reload without the cache's copy from the visit before the
// subscription, the page has the show's feed from the library alone, so
// its episodes stay from the library's copy while the feed is checked
// again, which here fails
test('unsubscribing after a reload keeps the episodes', async ({ page, context }) => {
  const main = page.getByRole('main');
  const unsubscribe = main.getByRole('button', { name: `Unsubscribe from ${showTitle}` });

  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(unsubscribe).toBeVisible();
  // deleted once the reload closes the page's connection
  await page.evaluate(() => { indexedDB.deleteDatabase('dk.simongray.elektrolyt.cache'); });
  await page.reload();
  await context.route(url => url.pathname === '/proxy' && url.searchParams.get('url') === feedUrl,
    route => route.abort());

  await unsubscribe.click();
  await expect(announcement(page)).toHaveText(`Unsubscribed from ${showTitle}`);
  await expect(main.getByRole('button', { name: `Subscribe to ${showTitle}` })).toBeVisible();
  await expect(page.getByRole('heading', { level: 1, name: showTitle })).toBeVisible();
  await expect(main.getByRole('link', { name: episodes[0].title })).toBeVisible();
});

// WCAG 2.2.2: the line under the status of a slow load moves for 5
// seconds at most, and not at all where the listener asks for less motion.
// The rows in the place of the episodes are for the eye alone.
test('a slow load moves a line under its status, unless motion is reduced', async ({ page, show }) => {
  const main = page.getByRole('main');
  const loading = main.getByRole('status').filter({ hasText: 'Loading the show…' });
  const animation = () => loading.evaluate(p => getComputedStyle(p, '::after').animationName);
  const skeleton = main.locator('ol.skeleton-rows');
  const release = show.hold();

  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto(showPath);
  await expect(loading).toBeVisible();
  expect(await animation()).toBe('busy');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  expect(await animation()).toBe('none');
  await expect(skeleton).toHaveAttribute('aria-hidden', 'true');
  await expect(main.getByRole('listitem')).toHaveCount(0);

  release();
  await expect(main.getByText(`${episodes.length} episodes.`, { exact: true })).not.toHaveClass(/busy/);
  await expect(skeleton).toHaveCount(0);
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

test('Back returns to where the page was left, with the focus on its heading', async ({ page }) => {
  // a phone's height, so that the Show page scrolls
  await page.setViewportSize({ width: 360, height: 480 });
  await page.goto(showPath);
  const main = page.getByRole('main');
  const link = main.getByRole('link', { name: episodes[0].title });
  await main.evaluate(m => { m.scrollTop = m.scrollHeight; });
  await link.scrollIntoViewIfNeeded();
  const left = await main.evaluate(m => m.scrollTop);
  expect(left).toBeGreaterThan(0);

  await link.click();
  await expect(main.getByRole('heading', { level: 1, name: episodes[0].title })).toBeFocused();
  await expect.poll(() => main.evaluate(m => m.scrollTop)).toBe(0);

  await page.goBack();
  await expect(main.getByRole('heading', { level: 1, name: showTitle })).toBeFocused();
  await expect.poll(() => main.evaluate(m => m.scrollTop)).toBe(left);

  await page.goForward();
  await expect(main.getByRole('heading', { level: 1, name: episodes[0].title })).toBeFocused();
  await expect.poll(() => main.evaluate(m => m.scrollTop)).toBe(0);
});
