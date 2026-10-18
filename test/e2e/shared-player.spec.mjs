// The tabs of the app share one playback: every tab's player shows the
// episode that plays in any of them, where it has come to, and its
// controls act on it there.

import { test, expect, episodePath, episodes } from './fixtures.mjs';

const [first] = episodes;

/** The player of the `page`. */
function player(page) {
  return page.getByRole('region', { name: 'Player' });
}

/** A promise of the position of the audio element of the `page`. */
function position(page) {
  return player(page).locator('audio').evaluate(a => a.currentTime);
}

/** A promise of whether the audio element of the `page` is paused. */
function paused(page) {
  return player(page).locator('audio').evaluate(a => a.paused);
}

test('one playback, which every tab shows and controls', async ({ page, context }) => {
  await page.goto(episodePath(first));
  await page.getByRole('main').getByRole('button', { name: 'Play', exact: true }).click();
  // the first run after a build starts the worker's code cold in every page
  await expect.poll(() => position(page), { timeout: 15_000 }).toBeGreaterThan(2);

  const other = await context.newPage();
  await other.goto('/');
  const there = player(other);
  await expect(there.getByRole('status')).toHaveText('Playing in another tab.');
  const seekBar = there.getByRole('slider', { name: 'Position' });
  const before = Number(await seekBar.inputValue());
  await expect.poll(async () => Number(await seekBar.inputValue()), { timeout: 5_000 })
    .toBeGreaterThan(before);

  // its Pause pauses the tab that plays
  await there.getByRole('button', { name: 'Pause' }).click();
  await expect.poll(() => paused(page)).toBe(true);
  const left = await position(page);
  await expect(there.getByRole('button', { name: 'Play', exact: true })).toBeVisible();

  // and its Play plays here, from where the other tab left it rather than
  // from where this tab loaded the episode
  await there.getByRole('button', { name: 'Play', exact: true }).click();
  await expect.poll(() => paused(other)).toBe(false);
  expect(await position(other)).toBeGreaterThanOrEqual(left - 0.5);
  await expect(player(page).getByRole('status')).toHaveText('Playing in another tab.');
  await expect.poll(() => paused(page)).toBe(true);
});
