// The picks of Home, Autoplay and the shows that a show links to, with
// shows of their own beside the fixtures' show: two about coffee, which
// the picks link, one about bikes, and one whose podroll names them. The
// routes of a test answer the proxy's requests for their feeds before
// those of the fixtures, which answer the rest, e.g. the audio.

import { createHash } from 'node:crypto';
import { test, expect, cached, navLink, playsPast } from './fixtures.mjs';

/** A show with a podcast:guid, its URL and its episodes, each a title and notes. */
function show(guid, title, episodes, podroll = []) {
  return { guid, title, url: `https://feeds.example/${guid}.xml`, episodes, podroll };
}

const coffee = show('11111111-1111-5111-8111-111111111111', 'Coffee Talk', [
  ['Espresso grinders', 'Burr grinders for espresso, and how fine to grind the beans.'],
  ['Roasting at home', 'Roasting espresso beans at home, for burr grinders.'],
]);

const brew = show('22222222-2222-5222-8222-222222222222', 'Brew Lab', [
  ['Grinders compared', 'Conical and flat burr grinders for espresso beans.'],
]);

const bikes = show('33333333-3333-5333-8333-333333333333', 'Bikes', [
  ['Gears', 'The gears of a bicycle.'],
]);

const flaky = show('44444444-4444-5444-8444-444444444444', 'Flaky Show', [
  ['Knots', 'Knots for a dinghy.'],
]);

const host = show('55555555-5555-5555-8555-555555555555', 'Podroll Host', [
  ['Hello', 'The shows we like.'],
], [brew, flaky]);

/** The guid of the episode `i` of the `show`. */
function itemGuid(show, i) {
  return `${show.guid}-${i}`;
}

/** The RSS feed of the `show`. */
function rss({ guid, title, episodes, podroll }) {
  const items = episodes.map(([episode, notes], i) => `
    <item>
      <guid isPermaLink="false">${itemGuid({ guid }, i)}</guid>
      <title>${episode}</title>
      <description>${notes}</description>
      <pubDate>${new Date(Date.UTC(2026, 9, 1 + i, 8)).toUTCString()}</pubDate>
      <enclosure url="https://media.example/${itemGuid({ guid }, i)}.mp3"
                 length="120000" type="audio/mpeg"/>
      <itunes:duration>120</itunes:duration>
    </item>`);
  const roll = podroll.length ? `
    <podcast:podroll>${podroll.map(s => `
      <podcast:remoteItem feedGuid="${s.guid}" feedUrl="${s.url}"/>`).join('')}
    </podcast:podroll>` : '';
  return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
     xmlns:podcast="https://podcastindex.org/namespace/1.0">
  <channel>
    <title>${title}</title>
    <link>https://show.example/${guid}</link>
    <description>A show for the tests of the picks.</description>
    <language>en</language>
    <podcast:guid>${guid}</podcast:guid>${roll}${items.toReversed().join('')}
  </channel>
</rss>
`;
}

/**
 * Answer the proxy's requests for the feeds of the `shows` in the `context`
 * of a test, and leave the rest to the fixtures. A show in the set that it
 * returns fails with a 404.
 */
async function serve(context, shows) {
  const failing = new Set();
  await context.route(url => url.pathname === '/proxy', route => {
    const url = new URL(route.request().url()).searchParams.get('url');
    const show = shows.find(s => s.url === url);
    if (!show) return route.fallback();
    if (failing.has(show)) return route.fulfill({ status: 404 });
    return route.fulfill({ contentType: 'application/rss+xml', body: rss(show) });
  });
  return failing;
}

/** The query that names the feed of the `show` in a link to one of its pages. */
function query(show) {
  return `?url=${encodeURIComponent(show.url)}`;
}

/** The path of the page of the `show`. */
function showPath(show) {
  return `/shows/${show.guid}${query(show)}`;
}

/** The path of the page of the episode `i` of the `show`, by the short hash of its guid. */
function episodePath(show, i) {
  const hash = createHash('sha1').update(itemGuid(show, i)).digest('hex').slice(0, 7);
  return `/shows/${show.guid}/episodes/${hash}${query(show)}`;
}

/** Subscribe to the `shows` with an OPML file, from Settings, and wait for their episodes. */
async function subscribe(page, shows) {
  const main = page.getByRole('main');
  const outlines = shows.map(s => `<outline type="rss" text="${s.title}" xmlUrl="${s.url}"/>`);
  await page.goto('/settings');
  await main.getByLabel('Import from an OPML file').setInputFiles({
    name: 'shows.opml',
    mimeType: 'text/x-opml',
    buffer: Buffer.from(`<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0"><head><title>Shows</title></head><body>${outlines.join('')}</body></opml>`),
  });
  await expect(main.getByRole('status').filter({ hasText: 'Subscribed' }))
    .toContainText(`Subscribed to ${shows.length} shows`);
  await navLink(page, 'Library').click();
  const list = main.getByRole('region', { name: 'Shows' }).getByRole('list');
  for (const { title } of shows) {
    await expect(list.getByRole('heading', { name: title })).toBeVisible();
  }
  await expect(list).toHaveAttribute('aria-busy', 'false');
}

/** Play the episode `i` of the `show` from its page, and give the player's audio element. */
async function play(page, show, i) {
  await page.goto(episodePath(show, i));
  await page.getByRole('main').getByRole('button', { name: 'Play', exact: true }).click();
  await playsPast(page, 1);
  return page.getByRole('region', { name: 'Player' }).locator('audio');
}

test("Home's picks are like the episode heard last, of other shows", async ({ page, context }) => {
  await serve(context, [coffee, brew, bikes]);
  await subscribe(page, [coffee, brew, bikes]);
  await play(page, coffee, 0);
  // a pause tells the library how far the listener heard it
  await page.getByRole('main').getByRole('button', { name: 'Pause' }).click();

  await navLink(page, 'Home').click();
  const picks = page.getByRole('main').getByRole('region', { name: 'You might like' });
  await expect(picks.getByRole('heading', { level: 3 })).toHaveText([brew.episodes[0][0]]);
  await expect(picks.getByRole('link', { name: 'grinders', exact: true })).toBeVisible();
});

test('Autoplay plays the first pick once the episode ends with nothing queued', async ({ page, context }) => {
  await serve(context, [coffee, brew, bikes]);
  await subscribe(page, [coffee, brew, bikes]);

  const audio = await play(page, coffee, 0);
  const title = page.getByRole('region', { name: 'Player' }).locator('.player-title a').first();
  await expect(title).toHaveText(coffee.episodes[0][0]);
  // The listener chooses Autoplay after play loads the episode's page, since
  // that load can cut the save of the choice short, e.g. while a refresh
  // holds the library.
  await navLink(page, 'Settings').click();
  await page.getByRole('main').getByRole('switch', { name: 'Autoplay' }).check();
  // The fixture's frames of 24 bytes have no room for a Xing header, so a
  // browser guesses its duration from its size, a little long. Chrome on
  // Linux plays to the end of its data and waits there without ending it,
  // so the test ends it as a browser does: a pause, then ended.
  await audio.evaluate(a => new Promise(resolve => {
    a.addEventListener('pause', () => {
      a.dispatchEvent(new Event('ended'));
      resolve();
    }, { once: true });
    a.pause();
  }));

  await expect(title).toHaveText(brew.episodes[0][0], { timeout: 15_000 });
  await expect.poll(() => audio.evaluate((a, guid) => !a.paused && a.src.includes(guid),
                                         itemGuid(brew, 0)))
    .toBe(true);
});

test('a show recommends the shows of its podroll, and asks again for those that failed', async ({ page, context }) => {
  const failing = await serve(context, [host, brew, flaky]);
  const podroll = page.getByRole('main').getByRole('region', { name: `Recommended by ${host.title}` });
  const titles = podroll.getByRole('heading', { level: 3 });

  failing.add(flaky);
  await page.goto(showPath(host));
  await expect(titles).toHaveText([brew.title, 'Untitled show']);

  // a fetch with a failure isn't kept, so the next visit asks again
  failing.delete(flaky);
  await page.reload();
  await expect(titles).toHaveText([brew.title, flaky.title]);

  // and one without is kept, once the page is idle
  await expect.poll(() => cached(page)).toContainEqual(expect.stringMatching(/^linked\./));
  failing.add(flaky);
  await page.reload();
  await expect(titles).toHaveText([brew.title, flaky.title]);
  await expect(podroll.getByRole('link', { name: brew.title })).toHaveAttribute('href', /22222222/);
});
