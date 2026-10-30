import { test, expect, announcement, navLink, openQueue, showPath, showTitle } from './fixtures.mjs';

const fourth = { guid: 'test-episode-4', title: 'Fourth episode', published: '2026-10-16T08:00:00Z' };

/** Subscribe to the show from its page. */
async function subscribe(page) {
  const main = page.getByRole('main');
  await page.goto(showPath);
  await main.getByRole('button', { name: `Subscribe to ${showTitle}` }).click();
  await expect(main.getByRole('button', { name: `Unsubscribe from ${showTitle}` })).toBeVisible();
}

/** Publish the fourth episode, and check for it from the Library page. */
async function checkForNewEpisodes(page, show) {
  const main = page.getByRole('main');
  show.publish(fourth);
  await navLink(page, 'Library').click();
  await main.getByRole('button', { name: 'Check for new episodes' }).click();
  await expect(announcement(page)).toHaveText('1 new episode');
}

test('a new episode arrives in the inbox, and leaves it once dismissed or queued', async ({ page, show }) => {
  const main = page.getByRole('main');
  await subscribe(page);
  await checkForNewEpisodes(page, show);

  // the inbox is a part of the Library
  const library = navLink(page, 'Library');
  await library.click();
  const section = main.getByRole('region', { name: 'Inbox' });
  await expect(section.getByRole('heading', { name: fourth.title })).toBeVisible();
  await section.getByRole('link', { name: 'The whole Inbox' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Inbox' })).toBeVisible();
  await expect(library).toHaveAttribute('aria-current', 'true');
  await expect(main.getByRole('listitem').getByRole('heading', { name: fourth.title })).toBeVisible();

  await main.getByRole('button', { name: `Dismiss ${fourth.title}` }).click();
  await expect(announcement(page)).toHaveText(`Dismissed ${fourth.title}`);
  await expect(main).toContainText('No new episodes');
  const undo = main.getByRole('button', { name: `Undo dismissing ${fourth.title}` });
  await expect(undo).toBeFocused();

  await undo.click();
  await expect(announcement(page)).toHaveText(`Put ${fourth.title} back in the inbox`);
  await expect(main.getByRole('button', { name: `Dismiss ${fourth.title}` })).toBeFocused();

  await main.getByRole('button', { name: `Play last ${fourth.title}` }).click();
  await expect(announcement(page)).toHaveText(`Added ${fourth.title} to the queue`);
  await expect(main).toContainText('No new episodes');
  await openQueue(page);
  await expect(main.getByRole('heading', { name: fourth.title })).toBeVisible();
});
