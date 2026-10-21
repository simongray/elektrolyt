// What the player has after a reload: the episode that the listener played
// last, even when the page reloads right after Play, before the save of
// the library is done. The show is subscribed, so that the library has its
// episodes; an episode of another show that Play adds to the library is
// only there once that save is done.

import { test, expect, episodePath, episodes, playsPast, showPath, showTitle } from './fixtures.mjs';

/** The title of the episode in the player of `page`. */
function playerTitle(page) {
  return page.getByRole('region', { name: 'Player' }).locator('.player-title p').first();
}

test('the player keeps the episode played last over a reload', async ({ page }) => {
  const main = page.getByRole('main');

  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();

  await page.goto(episodePath(episodes[0]));
  await main.getByRole('button', { name: 'Play', exact: true }).click();
  await expect(playerTitle(page)).toHaveText(episodes[0].title);
  await playsPast(page, 1);

  await page.goto(episodePath(episodes[1]));
  await main.getByRole('button', { name: 'Play', exact: true }).click();
  await expect(playerTitle(page)).toHaveText(episodes[1].title);

  await page.reload();
  await expect(playerTitle(page)).toHaveText(episodes[1].title);
});
