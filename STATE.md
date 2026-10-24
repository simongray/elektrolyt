State
=====

Every place where the app keeps something, with what it holds, who writes
it, who reads it, how long it lasts and what clears it. Each fact should
have one writer, so a new row needs a reason, and a fact in two places
says why.

The namespaces are those of `src/dk/simongray/elektrolyt/`, and
`elektrolyt` is the root namespace itself. Only `runtime`, `runtime.local`,
`runtime.log` and `worker.fetching` reach outside the store, which
`runtime_test.clj` checks.

The keys and databases that the browser keeps still carry the app's
former name, `dk.simongray.podcast-app`, and so do the keys of the app's
preferences in the profile. That way, what a listener saved before the
rename still opens.


In the tab's store
------------------

One atom, `elektrolyt/store`, which only effects change. Everything in it lasts
as long as the tab.

| Key | What it holds | Writers | Readers | Cleared |
|---|---|---|---|---|
| `:library` | The listener's library: the profile, the feeds and the search index, with this tab's operations that wait applied to it | `library` (`open!`, `change!`, `written!`, `caught-up!`) | most views, `player.audio`, `library.picks`, `library.refresh` | Replaced when another tab writes, or the page is shown again |
| `:player` | This tab's player: the episode, the element's media state, the volume, a seek, a wait for data, and what another tab plays | `player.audio` | `player`, the views | Overwritten |
| `:episodes` | What an episode's files say, by id: chapters, duration, transcript, and when their read began | `player.audio`, `show.feeds` | `player`, `episode.ui` | At a change of page, but for the episode that plays and the 20 whose reads began last (`episode/kept-count`) |
| `:feeds` | The feeds of shows outside the library, by URL, with their validators and whether this visit checked them | `show.feeds`, through `show` | `show/source` | Clear cache |
| `:feed-urls` | Where a show's feed is, by guid, from the cache or the Podcast Index | `show.feeds` | `show` | Clear cache, or a 404 or 410 at a URL from the cache |
| `:requests` | The requests for those feeds in flight | `show.feeds`, through `show` | `show/status` | When answered |
| `:episodes-shown` | How many episodes a Show page lists, by guid | `show` (`:show/more`) | `show.ui` | Never |
| `:searches` | The searches by query, each directory's results | `search.directories`, `search.ui` | `ui.routes`, `search`, `show`, `search.ui` | Clear cache |
| `:linked` | The shows that a show links to, by guid | `show.feeds` | `show.ui` | Clear cache |
| `:colors` | The colours of artwork, by URL | `ui.artwork.colors` | `ui.colors`, the views | Clear cache |
| `:refresh-status` | The refresh under way, and what the last one found, or when it failed or found another tab refreshing, which holds back the next | `library.refresh` | `library.refresh`, `library.opml`, `library.ui` | Overwritten |
| `:route` | The page | `ui.routes`, at each navigation | `ui`, `show`, `ui.colors` | Overwritten |
| `:server` | What the app's server offers | `elektrolyt`, once | `search`, `library.settings.ui` | Never |
| `:announcement` | The last status message for a screen reader | `library.changes`, `show`, `library.refresh`, `show.subscription` | `ui` | Overwritten |
| `:queue-undo`, `:inbox-undo` | The last change of the queue or the inbox that can be undone, which undoes only what's still as it left it | `library.changes`, through `library.queue` and `library.inbox` | `library.queue`, `library.inbox`, the views, which offer it while there's something to put back | This tab's next change |
| `:subscribing`, `:subscribe-errors` | The subscriptions under way, and those that failed, by URL | `show.subscription` | `ui.subscription` | Per URL, once it's done |
| `:settings-status` | The last outcome on Settings | `elektrolyt`, `library.settings`, `library.opml` | `library.settings.ui` | Overwritten |
| `:storage` | How the browser keeps the site's storage | `library/check-storage!` | `library.settings.ui` | At each check |
| `:library-view` | The Library page's order and layout | `elektrolyt` | `library.shows`, `library.ui` | Never |
| `:open` | The sections that the listener unfolded | `ui.controls` | `ui.controls` | Never, so that Back finds a section as the listener left it |


In the tab, outside the store
-----------------------------

| Place | What it holds | Writer | Readers | Lasts |
|---|---|---|---|---|
| `library/known` | The library that this tab last read or wrote, and its version | `library` | `library`, `library.refresh` | Until the next read or write |
| `library/waiting` | The operations that this tab applied and hasn't written | `library` (`change!`, `written!`) | `library` | Until they're written |
| `library/turns` | The chain of this tab's reads and writes, so that they take turns | `library` | `library` | The tab |
| `runtime/timers` | The timers that wait, by name: the next refresh, a prefetch, a wait for data and the clock of a player elsewhere | `library.refresh`, `show.feeds`, `player.audio`, through `runtime` | `runtime` | Until each fires or is cancelled |
| `runtime/channels` | The BroadcastChannels, each opened at its first use | `runtime` | `runtime` | The tab |
| `runtime/detach` | What stops following the player's audio element and clears the lock screen's buttons | `runtime/attach-player!` | `runtime/detach-player!` | While the element is mounted |
| `worker.fetching/web-worker` | The web worker, and the requests that it hasn't answered | `worker.fetching` | `library.refresh`, `show.feeds` | The tab, but a worker that fails stops, and the next ask starts another |
| `player.audio/tab` | This tab's id on the player's channel | `player.audio` | `player.audio` | The tab |
| `ui.memo` slots | The last result of a view that takes a while, e.g. the queue, a show's page, the picks | the views | the views | Until its arguments change |
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
| `…library` | The library's document, a document per feed, and the search index's | The tabs, with `library/durable-swap!`; the worker puts only feed documents | The tabs, with `library/read!`; the worker, with `fetch-due!` | Never by the app; the browser can evict it unless the site's storage persists |
| `…cache` | Directory results, feed URLs by guid, feeds of shows outside the library, linked shows, colours of artwork | `search.directories`, `show.feeds`, `ui.artwork.colors`, once the page is idle | the same | By age and count, and Clear cache |


Between the tabs and the worker
-------------------------------

| Place | What it carries | Writer | Readers |
|---|---|---|---|
| The library's channel | The version of the library that a tab wrote | `library/announce!` | `library`, which reads what's new |
| The player's channel | The playing tab's media state, a new tab's question, and commands for the playing tab | `player.audio` | `player.audio` |
| The play lock, a Web Lock | Which tab's element plays; a tab that plays takes it from the others | play-nice's `tabs/attach!`, through `runtime/attach-player!` | play-nice, which pauses the element that lost it |
| The refresh lock, a Web Lock | That a worker refreshes, so one does at a time | `worker.fetching`, in the worker | `worker.fetching`, which answers that it's busy |
| The worker's messages | What a refresh or a fetch asks for, and what it found, in EDN | `worker.fetching` | `worker.fetching` |


In the page
-----------

| Place | What it holds | Writer | Readers |
|---|---|---|---|
| The audio element | The episode's file, where it is, the volume and the rate | `player.audio`, through play-nice | `player.audio`, through `runtime/attach-player!` |
| The Media Session | The episode on the lock screen, and its buttons while the player's element is mounted | `runtime/lock-screen!`, from `player.audio` | The platform |
| `<html>`'s `data-theme` and `data-contrast`, the `theme-color` and the title | The look of the page | `ui.document`, at each render | CSS, the browser |
| The history | The page's URL; `ui.document/canonicalize!` changes it without a navigation, so `:route` keeps the URL that the link gave | `ui.routes`, `ui.document` | `runtime/follow-history!` |
| The listeners | The page shown, hidden or restored, the end of a transition, the channels' messages and the store's changes | `runtime`, for `elektrolyt`, `library`, `player.audio` and `library.opml` | — |
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
