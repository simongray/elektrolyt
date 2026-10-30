// The tests press the buttons of the queue with Enter, as a keyboard user
// does, since the focus stays in place for them.

import { test, expect, announcement, episodePath, episodes, openQueue, playsPast, showTitle }
  from './fixtures.mjs';

const [first, second, third] = episodes;

/**
 * Queue the first episode with Play last and the second with Play next,
 * which puts it after the first, and open the Queue page. The `page` goes
 * from one to the next by links, without a reload.
 */
async function queueTwo(page) {
  const main = page.getByRole('main');
  await page.goto(episodePath(first));
  await main.getByRole('button', { name: 'Play last' }).click();
  await expect(announcement(page)).toHaveText(`Added ${first.title} to the queue`);
  await main.getByRole('link', { name: showTitle }).click();
  await main.getByRole('link', { name: second.title }).click();
  await main.getByRole('button', { name: 'Play next' }).click();
  await expect(announcement(page)).toHaveText(`Playing ${second.title} next`);
  await openQueue(page);
}

/** The section of the episodes up next. */
function upNext(page) {
  return page.getByRole('region', { name: 'Up next' });
}

/** The titles of the episodes up next. */
function titles(page) {
  return upNext(page).getByRole('listitem').getByRole('heading', { level: 3 });
}

test('Play next and Play last turn into Unqueue, which keeps the focus', async ({ page }) => {
  const main = page.getByRole('main');
  const unqueue = main.getByRole('button', { name: 'Unqueue' });
  await page.goto(episodePath(first));

  for (const [name, added] of [['Play next', `Playing ${first.title} next`],
                               ['Play last', `Added ${first.title} to the queue`]]) {
    await main.getByRole('button', { name }).press('Enter');
    await expect(announcement(page)).toHaveText(added);
    await expect(main.getByRole('button', { name: /^Play (next|last)$/ })).toHaveCount(0);
    await expect(unqueue).toBeFocused();

    await unqueue.press('Enter');
    await expect(announcement(page)).toHaveText(`Removed ${first.title} from the queue`);
    await expect(main.getByRole('button', { name: 'Play next' })).toBeFocused();
  }
});

test('moving and removing an episode keep the focus in Up next', async ({ page }) => {
  await queueTwo(page);
  await expect(titles(page)).toHaveText([first.title, second.title]);

  const grip = upNext(page).getByRole('button', { name: `Move ${first.title}`, exact: true });
  await grip.press('Enter');
  await expect(grip).toHaveAttribute('aria-expanded', 'true');
  await grip.press('Tab');
  await expect(upNext(page).getByRole('button', { name: `Move down ${first.title}` })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(titles(page)).toHaveText([second.title, first.title]);
  await expect(grip).toBeFocused();
  await expect(grip).toHaveAttribute('aria-expanded', 'false');

  await upNext(page).getByRole('button', { name: `Remove ${second.title}` }).press('Enter');
  await expect(titles(page)).toHaveText([first.title]);
  await expect(announcement(page)).toHaveText(`Removed ${second.title} from the queue`);
  await expect(upNext(page).getByRole('button', { name: `Remove ${first.title}` }))
    .toBeFocused();

  await upNext(page).getByRole('button', { name: `Undo removing ${second.title}` })
    .press('Enter');
  await expect(titles(page)).toHaveText([second.title, first.title]);
  await expect(announcement(page)).toHaveText(`Put ${second.title} back in the queue`);
  await expect(upNext(page).getByRole('button', { name: `Remove ${second.title}` }))
    .toBeFocused();
});

test('an episode moves by a drag of its grip, which Escape puts back', async ({ page }) => {
  await queueTwo(page);
  const grip = title => upNext(page).getByRole('button', { name: `Move ${title}`, exact: true });
  const from = await grip(first.title).boundingBox();
  const to = await grip(second.title).boundingBox();
  const x = from.x + from.width / 2;

  await page.mouse.move(x, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(x, to.y + to.height, { steps: 8 });
  await page.keyboard.press('Escape');
  await page.mouse.up();
  await expect(titles(page)).toHaveText([first.title, second.title]);
  await expect(grip(first.title), 'nor does the release open its menu').toHaveAttribute('aria-expanded', 'false');

  await page.mouse.move(x, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(x, to.y + to.height, { steps: 8 });
  await page.mouse.up();
  await expect(titles(page)).toHaveText([second.title, first.title]);
  await expect(announcement(page)).toHaveText(`Moved ${first.title} to position 2 of 2`);
  await expect(grip(first.title)).toBeFocused();
});

// by the pointer, since the fan takes a press only on its items and grips
test("an episode moves by a drag of its grip in the player's fan, where a press on the grip plays nothing, and one outside only closes the fan", async ({ page }) => {
  await queueTwo(page);
  const main = page.getByRole('main');
  await upNext(page).getByRole('link', { name: first.title }).click();
  await main.getByRole('link', { name: showTitle }).click();
  await main.getByRole('link', { name: third.title }).click();
  await main.getByRole('button', { name: 'Play', exact: true }).click();
  await page.getByRole('button', { name: 'Queue', exact: true }).click();

  const fan = page.locator('#up-next-stack');
  const items = fan.locator('ol > li .stack-title');
  await expect(items).toHaveText([`Play ${first.title}`, `Play ${second.title}`]);
  const from = await fan.getByRole('button', { name: `Move ${first.title}`, exact: true }).boundingBox();
  const to = await fan.getByRole('button', { name: `Move ${second.title}`, exact: true }).boundingBox();
  const x = from.x + from.width / 2;

  const grip = fan.getByRole('button', { name: `Move ${first.title}`, exact: true });
  await page.mouse.click(x, from.y + from.height / 2);
  await expect(grip, 'its menu').toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator('#player .player-title')).toContainText(third.title);
  await expect(items).toHaveText([`Play ${first.title}`, `Play ${second.title}`]);

  await page.mouse.move(x, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(x, to.y + to.height, { steps: 8 });
  await page.mouse.up();
  await expect(items).toHaveText([`Play ${second.title}`, `Play ${first.title}`]);
  await expect(grip, 'which the drag closed').toHaveAttribute('aria-expanded', 'false');
  await expect(fan).toBeVisible();

  const url = page.url();
  await main.getByRole('link', { name: showTitle }).click();
  await expect(fan).toBeHidden();
  await expect(page, 'without following the link under the dimmed page').toHaveURL(url);
});

test('clearing the queue asks first, and moves the focus to Undo', async ({ page }) => {
  await queueTwo(page);
  const clear = upNext(page).getByRole('button', { name: 'Clear the queue' });
  const dialog = page.getByRole('dialog', { name: 'Clear the queue?' });
  const cancel = dialog.getByRole('button', { name: 'Cancel' });

  await clear.press('Enter');
  await expect(cancel).toBeFocused();
  await cancel.press('Enter');
  await expect(dialog).toBeHidden();
  await expect(clear).toBeFocused();
  await expect(titles(page)).toHaveCount(2);

  await clear.press('Enter');
  await dialog.getByRole('button', { name: 'Clear the queue' }).press('Enter');
  await expect(dialog).toBeHidden();
  await expect(titles(page)).toHaveCount(0);
  await expect(upNext(page).getByText('Nothing in the queue.')).toBeVisible();
  await expect(upNext(page).getByRole('button', { name: 'Undo clearing the queue' }))
    .toBeFocused();
});

test('a HEAD warms the next episode in the queue before the end, and its file plays after it', async ({ page }) => {
  await queueTwo(page);
  const next = `https://media.example/${second.guid}.mp3`;
  const methods = [];
  page.on('request', request => {
    if (request.url() === next) methods.push(request.method());
  });

  await page.goto(episodePath(first));
  await page.getByRole('main').getByRole('button', { name: 'Play', exact: true }).click();
  await playsPast(page, 1);
  expect(methods).toEqual([]);

  const audio = page.locator('#player audio');
  await audio.evaluate(a => { a.currentTime = a.duration - 5; });
  await expect.poll(() => methods).toEqual(['HEAD']);

  // The fixture's duration is a browser's guess, so the test ends the
  // episode as a browser does, as picks.spec.mjs explains
  await audio.evaluate(a => new Promise(resolve => {
    a.addEventListener('pause', () => {
      a.dispatchEvent(new Event('ended'));
      resolve();
    }, { once: true });
    a.pause();
  }));
  await expect(page.getByRole('region', { name: 'Player' }).locator('.player-title'))
    .toContainText(second.title, { timeout: 15_000 });
  await expect.poll(() => methods[0]).toBe('HEAD');
  await expect.poll(() => methods.slice(1)).toContain('GET');
});
