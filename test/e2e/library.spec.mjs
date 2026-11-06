import { test, expect, announcement, navLink, showPath, showTitle } from './fixtures.mjs';

const fourth = { guid: 'test-episode-4', title: 'Fourth episode', published: '2026-10-16T08:00:00Z' };

test('the shows take the room under one line of choices, which wraps on a phone', async ({ page }) => {
  const main = page.getByRole('main');
  const middle = async locator => {
    const { y, height } = await locator.boundingBox();
    return y + height / 2;
  };
  const sort = main.getByLabel('Sort by');
  const check = main.getByRole('button', { name: 'Check for new episodes' });
  const shows = main.getByRole('region', { name: 'Shows' }).getByRole('list');

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
  await navLink(page, 'Library').click();
  await expect(shows.getByRole('heading', { name: showTitle })).toBeVisible();

  expect(Math.abs(await middle(sort) - await middle(check))).toBeLessThan(2);
  const { width } = await shows.boundingBox();
  const inbox = await main.getByRole('heading', { name: 'Inbox' }).boundingBox();
  expect(width).toBeGreaterThan(inbox.width);

  await page.setViewportSize({ width: 320, height: 700 });
  expect(await middle(check)).toBeGreaterThan(await middle(sort));
  expect(await main.evaluate(m => m.scrollWidth <= m.clientWidth), 'with nothing wider than the phone').toBe(true);
});

test('checking for new episodes marks the shows busy, then says what it found', async ({ page, show }) => {
  const main = page.getByRole('main');
  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
  await navLink(page, 'Library').click();

  const shows = main.getByRole('region', { name: 'Shows' }).getByRole('list');
  await expect(shows.getByRole('heading', { name: showTitle })).toBeVisible();
  await expect(shows).toHaveAttribute('aria-busy', 'false');

  show.publish(fourth);
  const release = show.hold();
  await main.getByRole('button', { name: 'Check for new episodes' }).click();
  await expect(shows).toHaveAttribute('aria-busy', 'true');
  await expect(announcement(page)).toHaveText('Checking for new episodes…');

  release();
  await expect(shows).toHaveAttribute('aria-busy', 'false');
  await expect(announcement(page)).toHaveText('1 new episode');
});

/**
 * Count the view transitions that the page starts in window.__transitions,
 * where the browser has them, or take them away when `without`.
 */
async function transitions(page, without) {
  await page.addInitScript(without => {
    const start = Document.prototype.startViewTransition;
    if (!start) return;
    if (without) {
      delete Document.prototype.startViewTransition;
      return;
    }
    window.__transitions = 0;
    Document.prototype.startViewTransition = function (...args) {
      window.__transitions += 1;
      return start.apply(this, args);
    };
  }, without);
}

for (const [name, reducedMotion, without] of [
  ['in a view transition where motion is allowed', 'no-preference', false],
  ['at once in a browser without view transitions', 'no-preference', true],
  ['at once with reduced motion', 'reduce', false],
]) {
  test(`the Library's tiles change between grid and list ${name}`, async ({ page }) => {
    await transitions(page, without);
    await page.emulateMedia({ reducedMotion });
    const main = page.getByRole('main');
    await page.goto(showPath);
    await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
    await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
    await navLink(page, 'Library').click();
    const shows = main.getByRole('region', { name: 'Shows' }).getByRole('list');
    await expect(shows.getByRole('heading', { name: showTitle })).toBeVisible();
    const api = await page.evaluate(() => 'startViewTransition' in document);

    await main.getByLabel('Show as').selectOption('List');
    await expect(shows).toHaveClass(/\blist\b/);
    await main.getByLabel('Show as').selectOption('Grid');
    await expect(shows).toHaveClass(/\bgrid\b/);
    expect(await page.evaluate(() => window.__transitions))
      .toBe(api && reducedMotion === 'no-preference' ? 2 : api ? 0 : undefined);
  });
}

test("a change of the colours cross-fades the page, and other settings don't", async ({ page }) => {
  await transitions(page, false);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  const main = page.getByRole('main');
  await page.goto('/settings');
  const api = await page.evaluate(() => 'startViewTransition' in document);
  test.skip(!api, 'a browser without view transitions');

  await main.getByLabel('Light or dark').selectOption('Dark');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await main.getByRole('switch', { name: 'Colours from the artwork' }).click();
  await expect(main.getByRole('switch', { name: 'Colours from the artwork' })).not.toBeChecked();
  expect(await page.evaluate(() => window.__transitions)).toBe(1);
});
