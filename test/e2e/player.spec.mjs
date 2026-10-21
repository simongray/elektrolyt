import { test, expect, episodePath, episodes, playsPast } from './fixtures.mjs';

/** The `seconds` in words, as the seek bar's aria-valuetext has them under a minute. */
function inWords(seconds) {
  return `${seconds} ${seconds === 1 ? 'second' : 'seconds'}`;
}

test('a paused episode is paused at the same second after a reload', async ({ page }) => {
  await page.goto(episodePath(episodes[0]));
  const main = page.getByRole('main');
  const player = page.getByRole('region', { name: 'Player' });
  const audio = player.locator('audio');
  const seekBar = player.getByRole('slider', { name: 'Position' });

  await main.getByRole('button', { name: 'Play', exact: true }).click();
  await playsPast(page, 2);
  await main.getByRole('button', { name: 'Pause' }).click();
  await expect(player.getByRole('button', { name: 'Play', exact: true })).toBeVisible();

  const second = Math.floor(await audio.evaluate(a => a.currentTime));
  await expect(seekBar).toHaveValue(String(second));
  await expect(seekBar).toHaveAttribute('aria-valuetext', inWords(second));

  await page.reload();
  await expect.poll(() => audio.evaluate(a => a.readyState)).toBeGreaterThan(0);
  await expect(seekBar).toHaveValue(String(second));
  await expect(seekBar).toHaveAttribute('aria-valuetext', inWords(second));
  await expect(player.getByRole('button', { name: 'Play', exact: true })).toBeVisible();
  await expect.poll(() => audio.evaluate(a => a.paused)).toBe(true);
  await expect.poll(() => audio.evaluate(a => Math.floor(a.currentTime))).toBe(second);
});
