import { test, expect, showPath } from './fixtures.mjs';

// A PNG of 2 by 2 pixels, all blue
const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGNQcFgARAwQCgAaDgQBwpB3vQAAAABJRU5ErkJggg==',
  'base64');

// The server fetches the artwork itself and can't reach a host of the
// tests, so it sends the browser to the artwork, as it does with any
// artwork that it can't scale. Playwright doesn't route a request that a
// redirect makes, so the test checks the redirect rather than the image
// it leads to. podcast-clj's tests cover the thumbnails themselves.
test('a row of 48 pixels takes the server\'s smallest thumbnail of the artwork, which leads to the artwork when there is none', async ({ page, show }) => {
  show.artwork = { url: 'https://art.example/show.png', type: 'image/png', body: png };

  await page.goto(showPath);
  const row = page.getByRole('main').locator('li img[width="48"]').first();
  await expect.poll(() => row.evaluate(img => img.complete)).toBe(true);

  const thumbnail = new URL(await row.evaluate(img => img.currentSrc));
  expect(thumbnail.pathname).toBe('/artwork');
  expect(thumbnail.searchParams.get('url')).toBe(show.artwork.url);
  expect(thumbnail.searchParams.get('w')).toBe('64');
  await expect(row).toHaveAttribute('src', show.artwork.url);

  const response = await page.request.get(thumbnail.href, { maxRedirects: 0 });
  expect(response.status()).toBe(307);
  expect(response.headers().location).toBe(show.artwork.url);
});

test('a screen of twice the density takes the thumbnail of twice the width', async ({ browser, show }) => {
  show.artwork = { url: 'https://art.example/show.png', type: 'image/png', body: png };
  // a context of its own, which the fixture's routes don't reach
  const context = await browser.newContext({ deviceScaleFactor: 2 });
  await context.route(url => url.pathname === '/proxy', route => {
    const file = show.file(new URL(route.request().url()).searchParams.get('url'));
    return route.fulfill(file ? { contentType: file.type, body: file.body } : { status: 404 });
  });
  const page = await context.newPage();

  await page.goto(showPath);
  const row = page.getByRole('main').locator('li img[width="48"]').first();
  await expect.poll(() => row.evaluate(img => img.complete)).toBe(true);
  expect(new URL(await row.evaluate(img => img.currentSrc)).searchParams.get('w')).toBe('128');
  await context.close();
});
