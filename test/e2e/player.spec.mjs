import { test, expect, episodePath, episodes, playsPast } from './fixtures.mjs';

/** The `seconds` in words, as the seek bar's aria-valuetext has them under a minute. */
function inWords(seconds) {
  return `${seconds} ${seconds === 1 ? 'second' : 'seconds'}`;
}

// the stages of a start of playback, as User Timing marks them
test('a Play marks each stage up to the element playing, in order and within 3 seconds', async ({ page }) => {
  await page.goto(episodePath(episodes[0]));
  await page.getByRole('main').getByRole('button', { name: 'Play', exact: true }).click();
  await expect.poll(() => page.evaluate(() => performance.getEntriesByName('player/playing').length)).toBe(1);

  const marks = await page.evaluate(() => performance.getEntriesByType('mark')
    .filter(m => m.name.startsWith('player/'))
    .map(m => ({ name: m.name.slice('player/'.length), at: m.startTime })));
  const names = marks.map(m => m.name);
  const stages = ['asked', 'source', 'loadstart', 'loadedmetadata', 'canplay', 'playing'];
  expect(names).toEqual(expect.arrayContaining(['asked', 'source', 'playing']));
  expect(names, 'in the order of a start').toEqual(stages.filter(stage => names.includes(stage)));
  const at = Object.fromEntries(marks.map(m => [m.name, m.at]));
  expect(at.playing - at.asked).toBeLessThan(3000);
});

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
  // from the button before it
  await player.getByRole('button', { name: /^Forward/ }).focus();
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

test('Pause shows that the episode waits for its file, until it plays', async ({ page }) => {
  // the file comes 2 s late, as on a slow network or behind redirects
  await page.route('https://media.example/**', async route => {
    await new Promise(resolve => setTimeout(resolve, 2000));
    await route.fallback();
  });
  await page.goto(episodePath(episodes[0]));
  const main = page.getByRole('main');
  const player = page.getByRole('region', { name: 'Player' });

  await main.getByRole('button', { name: 'Play', exact: true }).click();
  await expect(main.getByRole('button', { name: 'Pause' })).toHaveAttribute('data-waiting', 'true');
  await expect(player.getByRole('button', { name: 'Pause' })).toHaveAttribute('data-waiting', 'true');
  await expect(player.getByRole('status')).toHaveText('Loading…');

  await playsPast(page, 1);
  await expect(main.getByRole('button', { name: 'Pause' })).not.toHaveAttribute('data-waiting');
  await expect(player.getByRole('button', { name: 'Pause' })).not.toHaveAttribute('data-waiting');
});

/** Make the `page` seem hidden, as with the screen off, or shown again. */
function seemHidden(page, hidden) {
  return page.evaluate(hidden => {
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => (hidden ? 'hidden' : 'visible'),
    });
    document.dispatchEvent(new Event('visibilitychange'));
  }, hidden);
}

// nobody sees the position move on a hidden page (ui/document.cljs)
test('a hidden page leaves the position where it was, and catches up once shown', async ({ page }) => {
  await page.goto(episodePath(episodes[0]));
  const seekBar = page.getByRole('region', { name: 'Player' }).getByRole('slider', { name: 'Position' });
  await page.getByRole('main').getByRole('button', { name: 'Play', exact: true }).click();
  await playsPast(page, 1);

  await seemHidden(page, true);
  const hiddenAt = Number(await seekBar.inputValue());
  await playsPast(page, hiddenAt + 2);
  expect(Number(await seekBar.inputValue()), 'as nothing renders the clock').toBe(hiddenAt);

  await seemHidden(page, false);
  await expect.poll(async () => Number(await seekBar.inputValue())).toBeGreaterThanOrEqual(hiddenAt + 2);
});
