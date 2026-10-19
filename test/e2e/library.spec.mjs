import { test, expect, announcement, navLink, showPath, showTitle } from './fixtures.mjs';

const fourth = { guid: 'test-episode-4', title: 'Fourth episode', published: '2026-10-16T08:00:00Z' };

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
