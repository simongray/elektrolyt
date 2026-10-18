// Two tabs of the app share the library in the browser's storage. Each is
// a replica of the device, and catches up with what the other saves, so
// that neither writes over the other.

import { test, expect, announcement, episodePath, episodes, navLink, showPath, showTitle }
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
  await page.getByRole('main').getByRole('button', { name: 'Add to queue' }).click();
  await expect(announcement(page)).toHaveText(`Added ${episode.title} to the queue`);
}

/** The titles of the episodes up next on the Queue page of the `page`. */
function upNext(page) {
  return page.getByRole('region', { name: 'Up next' })
    .getByRole('listitem').getByRole('heading', { level: 3 });
}

test('two tabs keep what each other queued', async ({ page, context }) => {
  const errors = [];
  await subscribe(page);
  const other = await otherTab(context, errors);
  await other.goto(episodePath(second));

  // each queues an episode that the other hasn't seen it queue
  await queue(page, first);
  await other.getByRole('main').getByRole('button', { name: 'Add to queue' }).click();
  await expect(announcement(other)).toHaveText(`Added ${second.title} to the queue`);

  await navLink(page, 'Queue').click();
  await expect(upNext(page)).toHaveText([first.title, second.title]);
  // after a reload, the first in the queue is the one playing
  await page.reload();
  await expect(page.getByRole('main').getByRole('heading', { level: 3 }))
    .toHaveText([first.title, second.title]);
  expect(errors, 'the errors that the other tab threw').toEqual([]);
});

test('a tab shows what another queues, without a reload', async ({ page, context }) => {
  const errors = [];
  await subscribe(page);
  const other = await otherTab(context, errors);
  await other.goto('/');
  await expect(other.getByRole('heading', { level: 1, name: 'Queue' })).toBeVisible();

  await queue(page, first);
  await expect(upNext(other)).toHaveText([first.title]);
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
