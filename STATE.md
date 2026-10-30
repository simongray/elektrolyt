State
=====

Every place where the app keeps something, with what it holds, who writes
it, who reads it, how long it lasts and what clears it. Each fact should
have one writer, so a new row needs a reason, and a fact in two places
says why.

The namespaces are those of `src/dk/simongray/elektrolyt/`, and
`elektrolyt` is the root namespace itself. Only `runtime`, `runtime.local`,
`runtime.log`, `runtime.perf` and `worker.fetching` reach outside the store,
which `runtime_test.clj` checks.

The names of the browser's keys and databases begin with
`dk.simongray.elektrolyt.`, which the tables shorten to `…`.


In the tab's store
------------------

One atom, `elektrolyt/store`, which only effects change. Everything in it lasts
as long as the tab.

| Key | What it holds | Writers | Readers | Cleared |
|---|---|---|---|---|
| `:library` | The listener's library: the profile, the feeds and the search index, which is read after the rest, with this tab's operations that wait applied to it | `library` (`open!`, `load-index!`, `change!`, `written!`, `caught-up!`) | most views, `player.audio`, `library.picks`, `library.refresh` | Replaced when another tab writes, or the page is shown again |
| `:player` | This tab's player: the episode, the element's media state, the volume, a seek, a wait for data, and what another tab plays | `player.audio` | `player`, the views | Overwritten |
| `:episodes` | What an episode's files say, by id: chapters, duration, transcript, and when their read began | `player.audio`, `show.feeds` | `player`, `episode.ui` | At a change of page, but for the episode that plays and the 20 whose reads began last (`episode/kept-count`) |
| `:feeds` | The feeds of shows outside the library, by URL, with their validators and whether this visit checked them, and the library's copy of the feed of a show that the listener unsubscribes from | `show.feeds` and `show.subscription`, through `show` | `show/source` | Clear cache, and at each new page all but the page's own, those in use and the five whole feeds fetched last (`show/forgotten`) |
| `:feed-urls` | Where a show's feed is, by guid, from the cache or the Podcast Index | `show.feeds` | `show` | Clear cache, or a 404 or 410 at a URL from the cache |
| `:requests` | The requests for those feeds in flight | `show.feeds`, through `show` | `show/status` | When answered |
| `:episodes-shown` | How many episodes a Show page lists, by guid | `show` (`:show/more`) | `show.ui` | Never |
| `:searches` | The searches by query, each directory's results | `search.directories`, `search.ui` | `ui.routes`, `search`, `show`, `search.ui` | Clear cache |
| `:linked` | The shows that a show links to, by guid | `show.feeds` | `show.ui` | Clear cache |
| `:picks` | The picks of each place that the page shows, e.g. Home or an open More like this, with the key that they were found for, found after the page paints | `library.picks.finding` | `library.picks`, the views, `ui.colors` | Overwritten, and a show's or an episode's go once another's are found |
| `:colors` | The colours of artwork, by URL | `ui.artwork.colors` | `ui.colors`, the views | Clear cache |
| `:refresh-status` | The refresh under way, and what the last one found, or when it failed or found another tab refreshing, which holds back the next | `library.refresh` | `library.refresh`, `library.ui` | Overwritten |
| `:route` | The page | `ui.routes`, at each navigation | `ui`, `show`, `ui.colors` | Overwritten |
| `:server` | What the app's server offers | `elektrolyt`, once | `search`, `library.settings.ui` | Never |
| `:announcement`, `:announcements` | The last status message for a screen reader, and how many there were, which makes each a new element so that a repeat is heard too | `library.changes`, `show`, `show.feeds`, `library.refresh`, `show.subscription`, `library.saves`, `library.settings`, `library.opml`, through `ui.announcement/announced` but for `show`'s count of episodes | `ui` | Overwritten |
| `:queue-undo`, `:inbox-undo` | The last change of the queue or the inbox that can be undone, which undoes only what's still as it left it | `library.changes`, through `library.queue` and `library.inbox` | `library.queue`, `library.inbox`, the views, which offer it while there's something to put back | This tab's next change |
| `:subscribing`, `:unsubscribing`, `:subscribe-errors` | The subscriptions under way, the unsubscriptions until the library lets the show go, a frame later, and the subscriptions that failed, by URL | `show.subscription` | `ui.subscription` | Per URL, once it's done |
| `:settings-status` | The last outcome on Settings | `elektrolyt`, `library.settings`, `library.opml` | `library.settings.ui` | Overwritten |
| `:storage` | How the browser keeps the site's storage | `library/check-storage!` | `library.settings.ui` | At each check |
| `:save-status` | While this tab's writes fail: when the oldest change that waits was made, how many writes in a row failed and why, which holds back the next try | `library`, through `library.saves` | `library`, `library.saves.ui`, for `ui` and `library.settings.ui` | Once a write goes through |
| `:library-view` | The Library page's order and layout | `elektrolyt` | `library.shows`, `library.ui` | Never |
| `:open` | The sections that the listener unfolded, the fan of a stack that they opened, e.g. the player's of what plays next, and a menu that they opened, e.g. of a row's moves | `ui.controls`, `ui.stack`, `ui.menu` | `ui.controls`, `ui.stack`, `ui.menu` | A section's never, so that Back finds it as the listener left it; a fan's at a press outside its stack, as the focus leaves it, at Escape, and at a press in it; a menu's at a press outside it, as the focus leaves it, at Escape, at an action, and as its row's drag starts |


In the tab, outside the store
-----------------------------

| Place | What it holds | Writer | Readers | Lasts |
|---|---|---|---|---|
| `library/known` | The library that this tab last read or wrote, and its version | `library` | `library`, `library.refresh` | Until the next read or write |
| `library/waiting` | The operations that this tab applied and hasn't written | `library` (`change!`, `written!`) | `library` | Until they're written |
| `library/turns` | The chain of this tab's reads and writes, so that they take turns | `library` | `library` | The tab |
| `runtime/timers` | The timers that wait, by name: the next refresh, a save tried again, a prefetch, a wait for data, the read of the files of an episode that was loaded but doesn't play, and the clock of a player elsewhere | `library.refresh`, `library`, `show.feeds`, `player.audio`, through `runtime` | `runtime` | Until each fires or is cancelled |
| `runtime/channels` | The BroadcastChannels, each opened at its first use | `runtime` | `runtime` | The tab |
| `runtime/detach` | What stops following the player's audio element and clears the lock screen's buttons | `runtime/attach-player!` | `runtime/detach-player!` | While the element is mounted |
| `runtime/scroll-positions` | Where the main content was scrolled to as each history entry was left, by the entry's key, for Back and Forward | `runtime/keep-scroll-positions!` | `runtime/scroll-position`, for `ui.routes/navigated` | The tab |
| `worker.fetching/web-worker` | The web worker, started at its first request or as the start of a show's feed is fetched ahead, and the requests that it hasn't answered | `worker.fetching` | `library.refresh`, `show.feeds` | The tab, but a worker that fails stops, and the next ask starts another |
| `worker.fetching/in-flight` | The promises of the worker's fetches of feeds in flight, by URL and options, which an equal fetch waits for, e.g. a subscription's while the Show page fetches the whole feed | `worker.fetching` | `worker.fetching`, for `show.feeds` | Until each settles |
| `player.audio/tab` | This tab's id on the player's channel | `player.audio` | `player.audio` | The tab |
| `player.audio/start` | The start of playback that the listener asked for last, with the time of each of its stages so far, for its User Timing marks and its log | `player.audio` | `player.audio` | Until the element plays, or the next start |
| `runtime.perf/worst` | The 20 slowest interactions and longest frames that the dev build logged, with their phases or scripts, the route and the target by tag and id, for `runtime.perf/report` | `runtime.perf` | `runtime.perf/report` | The tab |
| `runtime.perf/render-counts` | How many renders the page has made since its last frame and in all, for the measures of renders and dispatches | `runtime.perf` | `runtime.perf` | The tab, but the count of a frame until the next |
| `ui.memo` slots | The last result of a view that takes a while, e.g. the queue, the last four of a show's or an episode's page, and the runs of words that the notes of the last four feeds outside the library repeat, for their picks | the views, `library.picks` | the views | Until its arguments change, or four others are made after it |
| `library.picks.finding/pending` | The key that each place's picks are being found for, so that a change of the state meanwhile doesn't find them twice | `library.picks.finding` | `library.picks.finding` | Until they're found |
| `ui.memo/remember-each` maps | What each row derives from a string: the server's thumbnails of artwork by URL, and an episode's slug by its title and short hash by its guid | `ui.artwork`, `ui.routes` | the views, `ui.rows` | The tab, but each forgets the first of its strings past 2,000 |
| `ui.artwork.colors/arrived` | The colours of artworks worked out since the store last took them, which it takes together, with the next artworks marked as under way, once the page paints | `ui.artwork.colors` | `ui.artwork.colors` | Until the page paints |
| `ui.document/theme-waits` | Whether a reading of the theme colour waits for the next paint, so that the renders before it read it once | `ui.document` | `ui.document` | Until the paint |
| `ui.text/formatters` | The Intl formatters of dates, durations, lists, sizes and language names, by kind, language and options | `ui.text` | `ui.text`, `ui.time` | The tab |
| Telemere's handlers and minimum level | Its console handler, and the minimum level, which is `:warn` in a release build | `runtime.log/start!` | Telemere | The tab, or the worker |


In localStorage
---------------

Through `runtime.local`, apart from the device's id. Each lasts until the
browser clears the site's data.

| Key | What it holds | Writer | Readers | Cleared |
|---|---|---|---|---|
| `…device` | The id of this browser as a device | like-minded's `device/id!`, once, in `library/open!` | `library/open!` | Never |
| `…volume` | The level of the volume slider | `player.audio` | `player.audio`, as the page loads | Never |
| `…library-view` | The Library page's order and layout, which suit a screen | `elektrolyt` | `elektrolyt`, as the page loads | Never |
| `…theme`, `…contrast` | Copies of the listener's preferences, so that `public/theme.js` colours the page before it starts | `ui.document` | `public/theme.js` | When the theme follows the device again |


In IndexedDB
------------

| Database | What it holds | Writers | Readers | Cleared |
|---|---|---|---|---|
| `…library` | The library's document, a document per feed, each with only the notes that `resolve/description` reads, as the worker fetched it, and the search index's | The tabs, with `library/durable-swap!`; the worker puts only feed documents | The tabs, with `library/read!`; the worker, with `fetch-due!` | Never by the app; the browser can evict it unless the site's storage persists |
| `…cache` | Directory results, feed URLs by guid, feeds of shows outside the library, linked shows, colours of artwork, and whether DR publishes each series of drpodcast.nu, for a day | `search.directories`, `show.feeds`, `ui.artwork.colors`, once the page is idle | the same | By age and count, and Clear cache |
| `…opml` | The marks of each OPML import that wait for their feeds, a document per import, apart from the library's, whose other documents podcast-clj reads as its index | `library.opml`, at the import | `library.opml`, as a tab opens | Each once every show of its import has its feed, or the document doesn't read |


Between the tabs and the worker
-------------------------------

| Place | What it carries | Writer | Readers |
|---|---|---|---|
| The library's channel | The version of the library that a tab wrote | `library/announce!` | `library`, which reads what's new |
| The player's channel | The playing tab's media state, a new tab's question, and commands for the playing tab | `player.audio` | `player.audio` |
| The play lock, a Web Lock | Which tab's element plays; a tab that plays takes it from the others | play-nice's `tabs/attach!`, through `runtime/attach-player!` | play-nice, which pauses the element that lost it |
| The refresh lock, a Web Lock | That a worker refreshes, so one does at a time | `worker.fetching`, in the worker | `worker.fetching`, which answers that it's busy |
| The worker's messages | What a refresh, a fetch or a subscription's search documents ask for, and what they found, in EDN, with a feed, its documents or what a refresh found beside it in the library's codec | `worker.fetching` | `worker.fetching` |


In the page
-----------

| Place | What it holds | Writer | Readers |
|---|---|---|---|
| The audio element | The episode's file, where it is, the volume and the rate | `player.audio`, through play-nice | `player.audio`, through `runtime/attach-player!` |
| The Media Session | The episode on the lock screen, and its buttons while the player's element is mounted | `runtime/lock-screen!`, from `player.audio` | The platform |
| `<html>`'s `data-theme` and `data-contrast`, the `theme-color` and the title | The look of the page | `ui.document`, at each render | CSS, the browser |
| The history | The page's URL; `ui.document/canonicalize!` changes it without a navigation, so `:route` keeps the URL that the link gave | `ui.routes`, `ui.document` | `runtime/follow-history!` |
| The listeners | The page shown, hidden or restored, the end of a transition, a navigation, a tap on a link before the router starts, the channels' messages and the store's changes | `runtime`, for `elektrolyt`, `library`, `player.audio` and `library.opml` | — |
| The beforeunload listener | That the browser asks before the page goes, while its changes aren't saved | `runtime/warn-before-leaving!`, from `library` | The browser |
| The listeners of the logs | The errors and the rejected promises that nothing else caught, in the page and in the worker | `runtime.log` | Telemere |


Facts in two places
-------------------

- **The library** is in `:library`, in `library/known` and in IndexedDB.
  `:library` is `known` with the operations that wait, and IndexedDB is
  what every tab writes to.
- **The playback position** is in the element, in `:player`, in the
  profile of `:library`, and in IndexedDB, since the element alone knows
  where it is, and the library is told at moments.
- **The theme and the contrast** are preferences in the profile, with
  copies in localStorage for the first paint.
- **The Library page's view** is in `:library-view`, with a copy in
  localStorage for the next visit.
- **The route** is in `:route` and in the address bar, which
  `canonicalize!` changes alone.
- **A show outside the library** is in the store and in the cache, by
  key, so that the next visit opens it at once.
