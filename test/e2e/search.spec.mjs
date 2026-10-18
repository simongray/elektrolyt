// The Search page asks each directory that the listener turned on, and
// shows what each found in a list of its own, which stays as it appeared
// while the others answer.
//
// The directories' answers come from the routes below rather than the web:
// Apple at once, and fyyd a moment later. The test's server signs no
// requests to the Podcast Index, so a search leaves it out.

import { test, expect, navLink } from './fixtures.mjs';

/** Apple's answer to a search, of the `titles`. */
function apple(titles) {
  return {
    results: titles.map((title, i) => ({
      kind: 'podcast',
      collectionId: 1000 + i,
      collectionName: title,
      artistName: 'Someone',
      feedUrl: `https://apple.example/${i}.xml`,
    })),
  };
}

/** fyyd's answer to a search, of the `titles`. */
function fyyd(titles) {
  return {
    status: 1,
    data: titles.map((title, i) => ({
      id: 2000 + i,
      title,
      author: 'Someone else',
      xmlURL: `https://fyyd.example/${i}.xml`,
    })),
  };
}

/** Answer the directories' searches through the app's proxy, fyyd after `delay` ms. */
async function directories(context, delay) {
  await context.route(url => url.pathname === '/proxy', async route => {
    const url = new URL(new URL(route.request().url()).searchParams.get('url'));
    if (url.host === 'itunes.apple.com') {
      return route.fulfill({ json: apple(['Delta Green Stories', 'Agents of the Program']) });
    }
    if (url.host === 'api.fyyd.de') {
      await new Promise(resolve => setTimeout(resolve, delay));
      return route.fulfill({ json: fyyd(['RPPR Actual Play']) });
    }
    return route.fallback();
  });
}

test('each directory answers in a list of its own, which stays where it appeared', async ({ page, context }) => {
  await directories(context, 1500);
  const main = page.getByRole('main');
  await page.goto('/search?q=delta+green');

  const appleList = main.locator('section', { has: page.getByRole('heading', { name: 'Apple Podcasts', level: 2 }) });
  const first = appleList.getByRole('heading', { name: 'Delta Green Stories', level: 3 });
  await expect(first).toBeVisible();
  const before = await first.boundingBox();

  const fyydList = main.locator('section', { has: page.getByRole('heading', { name: 'fyyd', level: 2 }) });
  await expect(fyydList.getByRole('heading', { name: 'RPPR Actual Play', level: 3 })).toBeVisible();
  expect(await first.boundingBox(), "Apple's list didn't move as fyyd answered").toEqual(before);
  await expect(appleList.getByRole('heading', { level: 3 })).toHaveText(['Delta Green Stories', 'Agents of the Program']);

  await expect(main.getByRole('status').first()).toHaveText('Found 2 shows in Apple Podcasts and 1 in fyyd.');
  const jump = page.getByRole('navigation', { name: 'Directories' });
  await expect(jump.getByRole('link')).toHaveText(['Apple Podcasts 2 shows', 'fyyd 1 show']);
  const fyydLink = jump.getByRole('link', { name: /fyyd/ });
  await fyydLink.click();
  await expect(fyydList.getByRole('heading', { name: 'fyyd', level: 2 })).toBeFocused();
  await expect(fyydLink).toHaveAttribute('aria-current', 'true');
  // the whole of the list in view, sideways too where the lists sit in a row
  const shown = await fyydList.boundingBox();
  expect(shown.x).toBeGreaterThanOrEqual(0);
  expect(shown.x + shown.width).toBeLessThanOrEqual(page.viewportSize().width + 1);
});

test('a jump brings the whole of a list into view, where the row is too narrow for them all', async ({ page, context }) => {
  await directories(context, 0);
  await page.setViewportSize({ width: 900, height: 700 });
  const main = page.getByRole('main');
  await page.goto('/settings');
  await main.getByLabel('gpodder.net').check();
  await navLink(page, 'Search').click();
  await main.getByLabel('Search for podcasts, or paste a link').fill('delta green');
  await main.getByRole('button', { name: 'Search', exact: true }).click();

  const last = main.locator('section', { has: page.getByRole('heading', { name: 'gpodder.net', level: 2 }) });
  await expect(last.getByRole('heading', { level: 2 })).toBeVisible();
  const row = main.locator('.search-lists');
  const rowBox = await row.boundingBox();
  const before = await last.boundingBox();
  expect(before.x + before.width, 'cut off at the right at first').toBeGreaterThan(rowBox.x + rowBox.width);

  const jump = page.getByRole('navigation', { name: 'Directories' });
  await jump.getByRole('link', { name: /gpodder/ }).click();
  await expect(last.getByRole('heading', { level: 2 })).toBeFocused();
  const shown = await last.boundingBox();
  expect(shown.x).toBeGreaterThanOrEqual(rowBox.x - 1);
  expect(shown.x + shown.width).toBeLessThanOrEqual(rowBox.x + rowBox.width + 1);
  await expect(jump, 'sideways only, so the jump row stays in view').toBeInViewport();
});

test('a jump on a phone scrolls down to the list', async ({ page, context }) => {
  await directories(context, 0);
  await page.setViewportSize({ width: 375, height: 700 });
  const main = page.getByRole('main');
  await page.goto('/search?q=delta+green');
  const heading = main.getByRole('heading', { name: 'fyyd', level: 2 });
  await expect(heading).toBeAttached();
  await page.getByRole('navigation', { name: 'Directories' }).getByRole('link', { name: /fyyd/ }).click();
  await expect(heading).toBeFocused();
  await expect(heading).toBeInViewport();
});

test('the directories of a search are the ones turned on in Settings, in their order', async ({ page, context }) => {
  await directories(context, 0);
  const main = page.getByRole('main');
  await page.goto('/settings');
  await main.getByRole('button', { name: 'Move up fyyd' }).click();
  await main.getByRole('button', { name: 'Move up fyyd' }).click();
  await main.getByLabel('Apple Podcasts').uncheck();

  await navLink(page, 'Search').click();
  await expect(main).toContainText('Searches fyyd.');
  await main.getByLabel('Search for podcasts, or paste a link').fill('delta green');
  await main.getByRole('button', { name: 'Search', exact: true }).click();
  await expect(main.getByRole('heading', { level: 2 })).toHaveText(['fyyd']);
});
