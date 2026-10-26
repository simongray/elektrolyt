// What this browser keeps between visits besides the library: each
// directory's list for a day, which stands in for the directory later when
// it fails, and the feed of a show outside the library, which shows at once
// and is fetched again once it's past its time, with its new episodes
// behind a button. Settings clears them.
//
// The test of searches fixes the page's clock, so that days can pass
// between two loads, and a feed's server says max-age=1 where a show
// should be past its time.

import { test, expect, cached, episodes, feedUrl, navLink, showPath } from './fixtures.mjs';

const noon = new Date('2026-10-18T12:00:00Z');

const hour = 60 * 60 * 1000;

/** Answer Apple's searches through the proxy as `answer` says, and fyyd's with none. */
async function directories(context, answer) {
  await context.route(url => url.pathname === '/proxy', async route => {
    const url = new URL(new URL(route.request().url()).searchParams.get('url'));
    if (url.host === 'itunes.apple.com') {
      return answer(route);
    }
    if (url.host === 'api.fyyd.de') {
      return route.fulfill({ json: { status: 1, data: [] } });
    }
    return route.fallback();
  });
}

test('a search that the cache kept shows without asking again, and stands in for a directory that fails', async ({ page, context }) => {
  let asked = 0;
  let down = false;
  await directories(context, route => {
    asked += 1;
    if (down) return route.fulfill({ status: 503 });
    return route.fulfill({
      json: {
        results: [{
          kind: 'podcast',
          collectionId: 1000,
          collectionName: 'Delta Green Stories',
          artistName: 'Someone',
          feedUrl: 'https://apple.example/0.xml',
        }],
      },
    });
  });
  await page.clock.setFixedTime(noon);
  const main = page.getByRole('main');
  const appleList = main.locator('section', { has: page.getByRole('heading', { name: 'Apple Podcasts', level: 2 }) });
  const found = appleList.getByRole('heading', { name: 'Delta Green Stories', level: 3 });

  await page.goto('/search?q=delta+green');
  await expect(found).toBeVisible();
  await expect.poll(() => cached(page)).toContainEqual(expect.stringMatching(/^search\./));
  expect(asked).toBe(1);

  await page.reload();
  await expect(found).toBeVisible();
  await main.getByLabel('Search for podcasts, or paste a link').fill('Delta  Green');
  await main.getByRole('button', { name: 'Search', exact: true }).click();
  await expect(page).toHaveURL(/q=Delta/);
  await expect(found).toBeVisible();
  expect(asked, 'the reload and the same words, whatever their case, come from the cache').toBe(1);

  await page.clock.setFixedTime(new Date(noon.getTime() + 48 * hour));
  down = true;
  await page.reload();
  await expect(appleList).toContainText("Didn't answer, so these are its shows of 18 Oct 2026.");
  await expect(found).toBeVisible();
  expect(asked, 'a list two days old asks the directory again').toBe(2);
  await expect(appleList.getByRole('button', { name: /Try again/ })).toBeVisible();
});

/**
 * Count the requests for the show's feed through the proxy, in `count.n`,
 * and not the navigations, whose address names the feed too.
 */
function countFeedRequests(page) {
  const count = { n: 0 };
  page.on('request', request => {
    const url = new URL(request.url());
    if (url.pathname === '/proxy' && url.searchParams.get('url') === feedUrl) count.n += 1;
  });
  return count;
}

// The tests of shows keep the page's real clock, since Transit can't write
// the fake Date of Playwright's clock, and a feed has dates
test('a show outside the library opens from the cache, with no request while it is fresh', async ({ page }) => {
  const fetched = countFeedRequests(page);
  const main = page.getByRole('main');
  const newest = main.getByRole('link', { name: episodes[2].title });

  await page.goto(showPath);
  await expect(newest).toBeVisible();
  await expect.poll(() => cached(page)).toContainEqual(expect.stringMatching(/^show\./));
  const before = fetched.n;

  await page.reload();
  await expect(newest).toBeVisible();
  expect(fetched.n, 'a feed whose server names no time is fresh for an hour').toBe(before);
});

test('a show from the cache past its time is fetched again, and its new episodes wait for a button', async ({ page, show }) => {
  show.headers = { 'Cache-Control': 'max-age=1' };
  const fetched = countFeedRequests(page);
  const main = page.getByRole('main');

  await page.goto(showPath);
  await expect(main.getByRole('link', { name: episodes[2].title })).toBeVisible();
  await expect.poll(() => cached(page)).toContainEqual(expect.stringMatching(/^show\./));
  await page.waitForTimeout(1100);
  const before = fetched.n;

  show.publish({ guid: 'test-episode-4', title: 'Fourth episode', published: '2026-10-18T08:00:00Z' });
  await page.reload();
  const button = main.getByRole('button', { name: 'Show the new episode' });
  await expect(button).toBeVisible();
  expect(fetched.n, 'the feed alone, whole, not its start first').toBe(before + 1);
  await expect(main.getByRole('status').filter({ hasText: '3 episodes. 1 new episode is in.' })).toBeAttached();
  await expect(main.getByRole('link', { name: 'Fourth episode' })).toHaveCount(0);

  await button.click();
  await expect(main.getByRole('link', { name: 'Fourth episode' })).toBeVisible();
  await expect(main.getByRole('heading', { name: 'Episodes', level: 2 })).toBeFocused();
});

for (const width of [320, 900]) {
  test(`the button for new episodes takes the status line's place, so the list stays put, ${width} px wide`, async ({ page, show }) => {
    show.headers = { 'Cache-Control': 'max-age=1' };
    await page.setViewportSize({ width, height: 800 });
    const main = page.getByRole('main');
    const first = main.getByRole('link', { name: episodes[2].title }).first();

    await page.goto(showPath);
    await expect(first).toBeVisible();
    await expect.poll(() => cached(page)).toContainEqual(expect.stringMatching(/^show\./));
    await page.waitForTimeout(1100);

    show.publish({ guid: 'test-episode-4', title: 'Fourth episode', published: '2026-10-18T08:00:00Z' });
    const release = show.hold();
    await page.reload();
    await expect(first).toBeVisible();
    const before = (await first.boundingBox()).y;
    release();
    await expect(main.getByRole('button', { name: 'Show the new episode' })).toBeVisible();
    expect(Math.abs((await first.boundingBox()).y - before)).toBeLessThan(8);
  });
}

test('a show from the cache past its time that has not changed is asked about with its ETag, and stays', async ({ page, show }) => {
  show.headers = { 'Cache-Control': 'max-age=1', ETag: '"v1"' };
  const asked = [];
  page.on('request', request => {
    const url = new URL(request.url());
    if (url.pathname === '/proxy' && url.searchParams.get('url') === feedUrl) {
      asked.push(request.headers()['if-none-match']);
    }
  });
  const main = page.getByRole('main');

  await page.goto(showPath);
  await expect(main.getByRole('link', { name: episodes[2].title })).toBeVisible();
  await expect.poll(() => cached(page)).toContainEqual(expect.stringMatching(/^show\./));
  await page.waitForTimeout(1100);
  asked.length = 0;

  await page.reload();
  await expect.poll(() => asked).toEqual(['"v1"']);
  await expect(main.getByText('3 episodes.', { exact: true })).toBeVisible();
  await expect(main.getByRole('button', { name: /new episode/ })).toHaveCount(0);
});

test('Settings clears the searches and shows that this browser keeps', async ({ page }) => {
  const main = page.getByRole('main');
  await page.goto(showPath);
  await expect(main.getByRole('link', { name: episodes[2].title })).toBeVisible();
  await expect.poll(() => cached(page)).not.toEqual([]);

  await navLink(page, 'Settings').click();
  await main.getByRole('button', { name: 'Clear the searches and shows' }).click();
  await expect(main.getByText('Cleared the searches and shows.')).toBeVisible();
  expect(await cached(page)).toEqual([]);
});
