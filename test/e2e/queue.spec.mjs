// The tests press the buttons of the queue with Enter, as a keyboard user
// does, since the focus stays in place for them.

import { test, expect, announcement, episodePath, episodes, navLink, playsPast, showTitle }
  from './fixtures.mjs';

const [first, second] = episodes;

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
  await navLink(page, 'Queue').click();
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

  const moveDown = upNext(page).getByRole('button', { name: `Move down ${first.title}` });
  await moveDown.press('Enter');
  await expect(titles(page)).toHaveText([second.title, first.title]);
  await expect(moveDown).toBeFocused();
  await expect(moveDown).toHaveAttribute('aria-disabled', 'true');

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
