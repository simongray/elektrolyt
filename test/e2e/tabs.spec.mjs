// Two tabs of the app share the library in the browser's storage. Each
// writes its changes on condition of the version that it read, and again
// onto what the other wrote when the other wrote first, so that neither
// writes over the other.

import { test, expect, announcement, episodePath, episodes, navLink, openQueue, showPath, showTitle }
  from './fixtures.mjs';

const [first, second] = episodes;
const fourth = { guid: 'test-episode-4', title: 'Fourth episode', published: '2026-10-16T08:00:00Z' };

/** Another tab of the `context`, which fails the test when it throws. */
async function otherTab(context, errors) {
  const tab = await context.newPage();
  tab.on('pageerror', error => errors.push(error.message));
  return tab;
}

/** Subscribe to the show in the `page`, and wait for the library to have it. */
async function subscribe(page) {
  const main = page.getByRole('main');
  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
}

/** Queue the `episode` from its page in the `page`. */
async function queue(page, episode) {
  await page.goto(episodePath(episode));
  await page.getByRole('main').getByRole('button', { name: 'Play last' }).click();
  await expect(announcement(page)).toHaveText(`Added ${episode.title} to the queue`);
}

/** The titles of the episodes up next on the Queue page of the `page`. */
function upNext(page) {
  return page.getByRole('region', { name: 'Up next' })
    .getByRole('listitem').getByRole('heading', { level: 3 });
}

/** The titles of the newest episodes of the inbox on the Library page of the `page`. */
function inbox(page) {
  return page.getByRole('region', { name: 'Inbox' })
    .getByRole('listitem').getByRole('heading', { level: 3 });
}

test('two tabs keep what each other queued', async ({ page, context }) => {
  const errors = [];
  await subscribe(page);
  const other = await otherTab(context, errors);
  await other.goto(episodePath(second));

  // each queues an episode that the other hasn't seen it queue
  await queue(page, first);
  await other.getByRole('main').getByRole('button', { name: 'Play last' }).click();
  await expect(announcement(other)).toHaveText(`Added ${second.title} to the queue`);

  await openQueue(page);
  await expect(upNext(page)).toHaveText([first.title, second.title]);
  // after a reload, the first in the queue is the one playing
  await page.reload();
  await expect(page.getByRole('main').getByRole('region', { name: /^(Now playing|Up next)$/ })
    .getByRole('heading', { level: 3 }))
    .toHaveText([first.title, second.title]);
  expect(errors, 'the errors that the other tab threw').toEqual([]);
});

test('a tab shows what another queues, without a reload', async ({ page, context }) => {
  const errors = [];
  await subscribe(page);
  const other = await otherTab(context, errors);
  await other.goto('/queue');
  await expect(other.getByRole('heading', { level: 1, name: 'Queue' })).toBeVisible();

  await queue(page, first);
  await expect(upNext(other)).toHaveText([first.title]);
  expect(errors, 'the errors that the other tab threw').toEqual([]);
});

test('an undo that another tab left nothing to put back gives its focus to the heading', async ({ page, context }) => {
  const errors = [];
  await subscribe(page);
  await queue(page, first);
  await queue(page, second);
  await openQueue(page);
  const region = page.getByRole('region', { name: 'Up next' });
  await region.getByRole('button', { name: `Remove ${second.title}` }).press('Enter');
  const undo = region.getByRole('button', { name: `Undo removing ${second.title}` });
  await undo.focus();

  const other = await otherTab(context, errors);
  await queue(other, second);
  await expect(undo).toHaveCount(0);
  await expect(region.getByRole('heading', { level: 2, name: 'Up next' })).toBeFocused();
  // the first plays, as a page that loads takes the front of the queue
  await expect(upNext(page)).toHaveText([second.title]);
  expect(errors, 'the errors that the other tab threw').toEqual([]);
});

test('a refresh in one tab reaches the other', async ({ page, context, show }) => {
  const errors = [];
  await subscribe(page);
  const other = await otherTab(context, errors);
  await other.goto(showPath);
  const episodesOf = tab => tab.getByRole('main').getByRole('heading', { level: 3 });
  await expect(episodesOf(other)).toHaveCount(3);

  show.publish(fourth);
  await navLink(page, 'Library').click();
  await page.getByRole('main').getByRole('button', { name: 'Check for new episodes' }).click();
  await expect(announcement(page)).toHaveText('1 new episode');
  await expect(episodesOf(other).filter({ hasText: fourth.title })).toHaveCount(1);
  expect(errors, 'the errors that the other tab threw').toEqual([]);
});

/** The button that queues the episode of the Episode page in the `page`. */
function addToQueue(page) {
  return page.getByRole('main').getByRole('button', { name: 'Play last' });
}

/** The headings of the episodes of the queue on the Queue page of the `page`. */
function queueHeadings(page) {
  return page.getByRole('main').getByRole('region', { name: /^(Now playing|Up next)$/ })
    .getByRole('heading', { level: 3 });
}

/** The titles of the episodes of the queue of the `page`, in alphabetical order. */
async function queued(page) {
  return (await queueHeadings(page).allTextContents()).toSorted();
}

test('two tabs that change the library at once keep both changes', async ({ page, context }) => {
  const errors = [];
  await subscribe(page);
  const other = await otherTab(context, errors);
  await page.goto(episodePath(first));
  await other.goto(episodePath(second));
  await expect(addToQueue(page)).toBeVisible();
  await expect(addToQueue(other)).toBeVisible();

  // both change the version that they read, so the one that writes second
  // writes again onto what the first wrote. A dispatched click skips
  // Playwright's checks before a click, which take long enough that one tab
  // most often reads what the other wrote before it changes the library.
  await Promise.all([addToQueue(page).dispatchEvent('click'), addToQueue(other).dispatchEvent('click')]);
  await expect(announcement(page)).toHaveText(`Added ${first.title} to the queue`);
  await expect(announcement(other)).toHaveText(`Added ${second.title} to the queue`);

  for (const tab of [page, other]) {
    await openQueue(tab);
    await expect(upNext(tab)).toHaveCount(2);
    expect(await queued(tab), 'the queue of each tab').toEqual([first.title, second.title]);
  }
  await page.reload();
  await expect(queueHeadings(page)).toHaveCount(2);
  expect(await queued(page), 'the queue after a reload').toEqual([first.title, second.title]);
  expect(errors, 'the errors that the other tab threw').toEqual([]);
});

test('a refresh in one tab and a change in the other at once keep both', async ({ page, context, show }) => {
  const errors = [];
  await subscribe(page);
  const other = await otherTab(context, errors);
  await other.goto(episodePath(first));
  await expect(addToQueue(other)).toBeVisible();
  show.publish(fourth);
  await navLink(page, 'Library').click();
  const check = page.getByRole('main').getByRole('button', { name: 'Check for new episodes' });

  await Promise.all([check.dispatchEvent('click'), addToQueue(other).dispatchEvent('click')]);
  await expect(announcement(page)).toHaveText('1 new episode');
  await expect(announcement(other)).toHaveText(`Added ${first.title} to the queue`);

  for (const tab of [page, other]) {
    await navLink(tab, 'Library').click();
    await expect(inbox(tab), 'the new episode in the inbox of each tab').toHaveText([fourth.title]);
    await openQueue(tab);
    await expect(upNext(tab), 'and the queue of each').toHaveText([first.title]);
  }
  await page.reload();
  await expect(queueHeadings(page), 'both after a reload').toHaveText([first.title]);
  await navLink(page, 'Library').click();
  await expect(inbox(page)).toHaveText([fourth.title]);
  expect(errors, 'the errors that the other tab threw').toEqual([]);
});
