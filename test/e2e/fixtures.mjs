// Fixtures for the end-to-end tests: a show whose feed and audio come from
// the tests rather than the web, and a page that fails its test when it
// throws.
//
// The app fetches feeds through its server's proxy, which refuses URLs on
// the server's own machine. So the show's feed has a URL under .example
// (RFC 2606), and the routes below answer the proxy's requests for it in
// the browser, before they reach the server. The audio of the episodes
// comes from .example too: two minutes of silence, made with lame. An
// episode needs more than a minute, since podcast-clj counts its last 30
// seconds as heard.
//
//     head -c 3840000 /dev/zero | lame -r -s 16 --resample 16 \
//       --bitwidth 16 --signed --little-endian -m m -b 8 --cbr - silence.mp3

import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { test as base, errors, expect } from '@playwright/test';

export { expect };

export const feedUrl = 'https://feeds.example/show.xml';
export const showGuid = '0f7c2b4e-5d1a-5e3b-9c8d-6a2f1e0b7d43';
export const showTitle = 'The Test Show';

const audio = readFileSync(new URL('silence.mp3', import.meta.url));

/** The episodes of the show, oldest first. */
export const episodes = [
  { guid: 'test-episode-1', title: 'First episode', published: '2026-10-01T08:00:00Z' },
  { guid: 'test-episode-2', title: 'Second episode', published: '2026-10-08T08:00:00Z' },
  { guid: 'test-episode-3', title: 'Third episode', published: '2026-10-15T08:00:00Z' },
];

/** The RSS item of the `episode`. */
function item({ guid, title, published }) {
  return `
    <item>
      <guid isPermaLink="false">${guid}</guid>
      <title>${title}</title>
      <pubDate>${new Date(published).toUTCString()}</pubDate>
      <enclosure url="https://media.example/${guid}.mp3"
                 length="${audio.length}" type="audio/mpeg"/>
      <itunes:duration>120</itunes:duration>
    </item>`;
}

/** The feed of the show with the `episodes`, newest first, and its `artwork`, if any. */
function rss(episodes, artwork) {
  const newest = episodes.toSorted((a, b) => b.published.localeCompare(a.published));
  return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
     xmlns:podcast="https://podcastindex.org/namespace/1.0">
  <channel>
    <title>${showTitle}</title>
    <link>https://show.example/</link>
    <description>A show for the end-to-end tests.</description>
    <language>en</language>
    <podcast:guid>${showGuid}</podcast:guid>
    <itunes:author>The Testers</itunes:author>${artwork ? `
    <itunes:image href="${artwork.url}"/>` : ''}${newest.map(item).join('')}
  </channel>
</rss>
`;
}

/** The query that names the show's feed in a link to one of its pages. */
const query = `?url=${encodeURIComponent(feedUrl)}`;

/** The path of the show's page. */
export const showPath = `/shows/${showGuid}${query}`;

/** The path of the page of the `episode`, by the short hash of its guid. */
export function episodePath({ guid }) {
  const hash = createHash('sha1').update(guid).digest('hex').slice(0, 7);
  return `/shows/${showGuid}/episodes/${hash}${query}`;
}

/** The status line of main, which only a screen reader reads. */
export function announcement(page) {
  return page.locator('main > [role=status].visually-hidden');
}

/** The link to the page titled `title` in the main navigation. */
export function navLink(page, title) {
  return page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: title });
}

/**
 * The names of the documents in the cache's database, which is opened as
 * like-minded opens it, so that a test that comes first makes it the same.
 */
export function cached(page) {
  return page.evaluate(() => new Promise((resolve, reject) => {
    const request = indexedDB.open('dk.simongray.podcast-app.cache', 1);
    request.onupgradeneeded = () => {
      request.result.createObjectStore('bodies');
      request.result.createObjectStore('versions');
    };
    request.onerror = () => reject(request.error);
    request.onsuccess = () => {
      const db = request.result;
      const keys = db.transaction('versions').objectStore('versions').getAllKeys();
      keys.onsuccess = () => {
        db.close();
        resolve(keys.result);
      };
    };
  }));
}

/**
 * The show that the routes serve. A test can publish an episode, hold the
 * fetches of the feed until it releases them, give the feed headers, e.g.
 * a Cache-Control, or give the show artwork, as a map of its url, its type
 * and its body.
 */
class Show {
  episodes = [...episodes];
  held = null;
  headers = {};
  artwork = null;

  publish(episode) {
    this.episodes.push(episode);
  }

  /** Hold the fetches of the feed, and return the function that releases them. */
  hold() {
    let release;
    this.held = new Promise(resolve => { release = resolve; });
    return () => {
      this.held = null;
      release();
    };
  }

  /** The type, the body and the headers of the file at `url`, or undefined when there's none. */
  file(url) {
    if (url === feedUrl) {
      return { type: 'application/rss+xml', body: Buffer.from(rss(this.episodes, this.artwork)), headers: this.headers };
    }
    if (url && url === this.artwork?.url) {
      return { type: this.artwork.type, body: this.artwork.body };
    }
    if (url?.startsWith('https://media.example/')) {
      return { type: 'audio/mpeg', body: audio };
    }
  }
}

// RFC 9110, section 14: a browser seeks in an audio file only when its
// server answers requests for ranges
/** Answer the `route` with the `file`, or the range of it that it asks for. */
function answer(route, file) {
  if (!file) return route.fulfill({ status: 404 });
  const { type, body, headers } = file;
  // RFC 9110, section 13.1.2: a request that names the ETag of the file has it already
  const etag = headers?.ETag;
  if (etag && route.request().headers()['if-none-match']?.includes(etag)) {
    return route.fulfill({ status: 304, headers });
  }
  const size = body.length;
  const range = /^bytes=(\d*)-(\d*)$/.exec(route.request().headers().range ?? '');
  if (!range || (!range[1] && !range[2])) {
    return route.fulfill({ contentType: type, body, headers: { 'Accept-Ranges': 'bytes', ...headers } });
  }
  const [, from, to] = range;
  const start = from ? Number(from) : Math.max(0, size - Number(to));
  const end = from && to ? Math.min(Number(to), size - 1) : size - 1;
  if (start >= size) {
    return route.fulfill({ status: 416, headers: { 'Content-Range': `bytes */${size}` } });
  }
  return route.fulfill({
    status: 206,
    contentType: type,
    body: body.subarray(start, end + 1),
    headers: { 'Accept-Ranges': 'bytes', 'Content-Range': `bytes ${start}-${end}/${size}` },
  });
}

/** Whether the `browser` makes a context within five seconds. One that works takes milliseconds. */
async function responds(browser) {
  let timer;
  const timeout = new Promise(resolve => { timer = setTimeout(resolve, 5_000, false); });
  const context = browser.newContext();
  const responded = await Promise.race([context.then(() => true), timeout]);
  clearTimeout(timer);
  if (responded) await (await context).close();
  return responded;
}

/** Kill the processes that this worker started, and theirs. */
function killChildren() {
  const pids = spawnSync('pgrep', ['-P', String(process.pid)]).stdout.toString().split('\n').filter(Boolean);
  // Playwright starts each browser as the leader of a process group
  for (const pid of pids) process.kill(-Number(pid), 'SIGKILL');
}

export const test = base.extend({
  // Now and then Chrome on macOS hangs for good as it starts, more often
  // when several start at once. Its main thread asks RunningBoard for an
  // App Nap assertion and never wakes up (rbs_acquire_appnap_assertion in
  // a sample of the process). The launch or the first newContext then
  // times out the test, and Chrome won't even close. So each worker gives
  // the browser it launches ten seconds to start and checks that it
  // responds. When it doesn't, the worker kills it and launches another.
  // (The stuck browser is the only process the worker started.)
  browser: [async ({ playwright, browserName }, use) => {
    for (let launch = 1; launch <= 3; launch++) {
      const browser = await playwright[browserName].launch({ timeout: 10_000 }).catch(error => {
        if (!(error instanceof errors.TimeoutError)) throw error;
      });
      if (browser && await responds(browser)) {
        await use(browser);
        await browser.close();
        return;
      }
      console.warn(`${browserName} got stuck as it started, so the worker launches it again`);
      killChildren();
    }
    throw new Error(`${browserName} got stuck in each of 3 launches`);
  }, { scope: 'worker', timeout: 0 }],

  show: [async ({ context }, use) => {
    const show = new Show();
    await context.route(url => url.pathname === '/proxy', async route => {
      const url = new URL(route.request().url()).searchParams.get('url');
      if (url === feedUrl) await show.held;
      await answer(route, show.file(url));
    });
    await context.route('https://media.example/**', route =>
      answer(route, show.file(route.request().url())));
    await context.route('https://art.example/**', route =>
      answer(route, show.file(route.request().url())));
    await use(show);
  }, { auto: true }],

  page: async ({ page }, use) => {
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await use(page);
    expect(errors, 'the errors that the page threw').toEqual([]);
  },
});
