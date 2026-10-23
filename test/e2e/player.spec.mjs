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

test('the keyboard reaches the volume from the speaker, and Escape hides it', async ({ page }) => {
  await page.goto(episodePath(episodes[0]));
  const player = page.getByRole('region', { name: 'Player' });
  const audio = player.locator('audio');
  const speaker = player.getByRole('button', { name: 'Mute', exact: true });
  const slider = player.getByRole('slider', { name: 'Volume' });

  await page.getByRole('main').getByRole('button', { name: 'Play', exact: true }).click();
  await expect(slider).toBeHidden();
  // from the speed before it
  await player.getByRole('combobox', { name: 'Speed' }).focus();
  await page.keyboard.press('Tab');
  await expect(speaker).toBeFocused();
  await expect(slider).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(slider).toBeFocused();

  await playsPast(page, 2);
  await expect(slider).toBeVisible();
  await page.keyboard.press('Home');
  await expect.poll(() => audio.evaluate(a => a.volume)).toBe(0);
  await expect(slider).toHaveAttribute('aria-valuetext', '0%');
  await page.keyboard.press('ArrowUp');
  await expect(slider).toHaveAttribute('aria-valuetext', '5%');

  await page.keyboard.press('Escape');
  await expect(slider).toBeHidden();
  await expect(speaker).toBeFocused();
});

test('a mouse resting on the speaker shows the volume, and a click mutes', async ({ page }) => {
  await page.goto(episodePath(episodes[0]));
  const player = page.getByRole('region', { name: 'Player' });
  const audio = player.locator('audio');
  const speaker = player.getByRole('button', { name: 'Mute', exact: true });
  const slider = player.getByRole('slider', { name: 'Volume' });

  await page.getByRole('main').getByRole('button', { name: 'Play', exact: true }).click();
  await speaker.hover();
  await expect(slider).toBeVisible();
  await speaker.click();
  await expect.poll(() => audio.evaluate(a => a.muted)).toBe(true);
  await expect(player.getByRole('button', { name: 'Unmute', exact: true })).toBeVisible();
  await expect(slider).toBeVisible();
  await expect(slider).toHaveAttribute('aria-valuetext', '0%');

  // moving the slider ends the mute
  await slider.focus();
  await page.keyboard.press('ArrowUp');
  await expect.poll(() => audio.evaluate(a => a.muted)).toBe(false);

  // and it goes once the mouse and the focus have
  await slider.blur();
  await page.mouse.move(0, 0);
  await expect(slider).toBeHidden();
});
