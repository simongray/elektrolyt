State
=====

Every place where the app keeps something, with what it holds, who writes
it, who reads it, how long it lasts and what clears it. Each fact should
have one writer, so a new row needs a reason, and a fact in two places
says why.

The namespaces are those of `src/dk/simongray/podcast_app/`. Only
`runtime`, `fetching` and `local` reach outside the store, which
`runtime_test.clj` checks.


In the tab's store
------------------

One atom, `main/store`, which only effects change. Everything in it lasts
as long as the tab.

| Key | What it holds | Writers | Readers | Cleared |
|---|---|---|---|---|
| `:library` | The listener's library: the profile, the feeds and the search index, with this tab's operations that wait applied to it | `library` (`open!`, `change!`, `written!`, `caught-up!`) | most views, `audio`, `picks`, `refresh` | Replaced when another tab writes, or the page is shown again |
| `:player` | This tab's player: the episode, the element's media state, the volume, a seek, a wait for data, and what another tab plays | `audio`, and `main` for `:expanded?` | `player`, the views | Overwritten |
| `:episodes` | What an episode's files say, by id: chapters, duration, transcript | `audio`, `feeds` | `player`, `ui/episode` | Never |
| `:feeds` | The feeds of shows outside the library, by URL, with their validators and whether this visit checked them | `feeds`, through `show` | `show/source` | Clear cache |
| `:feed-urls` | Where a show's feed is, by guid, from the cache or the Podcast Index | `feeds` | `show` | Clear cache, or a 404 or 410 at a URL from the cache |
| `:requests` | The requests for those feeds in flight | `feeds`, through `show` | `show/status` | When answered |
| `:episodes-shown` | How many episodes a Show page lists, by guid | `show` (`:show/more`) | `ui/show` | Never |
| `:searches` | The searches by query, each directory's results | `searches`, `ui/search` | `routes`, `search`, `show`, `ui/search` | Clear cache |
| `:linked` | The shows that a show links to, by guid | `feeds` | `ui/show` | Clear cache |
| `:colors` | The colours of artwork, by URL | `artwork-colors` | `colors`, the views | Clear cache |
| `:refresh-status` | The refresh under way, and what the last one found | `refresh` | `refresh`, `opml`, `ui/library` | Overwritten |
| `:route` | The page | `routes`, at each navigation | `ui`, `show`, `colors` | Overwritten |
| `:server` | What the app's server offers | `main`, once | `search`, `ui/settings` | Never |
| `:announcement` | The last status message for a screen reader | `changes`, `show`, `refresh`, `subscription` | `ui` | Overwritten |
| `:queue-undo`, `:inbox-undo` | The last change of the queue or the inbox that can be undone | `changes`, through `queue` and `inbox` | `queue`, `inbox`, the views | This tab's next change |
| `:subscribing`, `:subscribe-errors` | The subscriptions under way, and those that failed, by URL | `subscription` | `ui/subscription` | Per URL, once it's done |
| `:settings-status` | The last outcome on Settings | `main`, `settings`, `opml` | `ui/settings` | Overwritten |
| `:storage` | How the browser keeps the site's storage | `library/check-storage!` | `ui/settings` | At each check |
| `:library-view` | The Library page's order and layout | `main` | `shows`, `ui/library` | Never |
| `:open` | The sections that the listener unfolded | `ui/controls` | `ui/controls` | Never |


In the tab, outside the store
-----------------------------

| Place | What it holds | Writer | Readers | Lasts |
|---|---|---|---|---|
| `library/known` | The library that this tab last read or wrote, and its version | `library` | `library`, `refresh` | Until the next read or write |
| `library/waiting` | The operations that this tab applied and hasn't written | `library` (`change!`, `written!`) | `library` | Until they're written |
| `library/turns` | The chain of this tab's reads and writes, so that they take turns | `library` | `library` | The tab |
| `runtime/timers` | The timers that wait, by name: the next refresh, a prefetch, a wait for data and the clock of a player elsewhere | `refresh`, `feeds`, `audio`, through `runtime` | `runtime` | Until each fires or is cancelled |
| `runtime/channels` | The BroadcastChannels, each opened at its first use | `runtime` | `runtime` | The tab |
| `runtime/detach` | What stops following the player's audio element | `runtime/attach-player!` | `runtime/detach-player!` | While the element is mounted |
| `fetching/web-worker` | The web worker, and the requests that it hasn't answered | `fetching` | `refresh`, `feeds` | The tab |
| `audio/tab` | This tab's id on the player's channel | `audio` | `audio` | The tab |
| `memo` slots | The last result of a view that takes a while, e.g. the queue, a show's page, the picks | the views | the views | Until its arguments change |


In localStorage
---------------

Through `local`, apart from the device's id. Each lasts until the browser
clears the site's data.

| Key | What it holds | Writer | Readers | Cleared |
|---|---|---|---|---|
| `…device` | The id of this browser as a device | like-minded's `device/id!`, once, in `library/open!` | `library/open!` | Never |
| `…volume` | The level of the volume slider | `audio` | `audio`, as the page loads | Never |
| `…library-view` | The Library page's order and layout, which suit a screen | `main` | `main`, as the page loads | Never |
| `…theme`, `…contrast` | Copies of the listener's preferences, so that `public/theme.js` colours the page before it starts | `page` | `public/theme.js` | When the theme follows the device again |


In IndexedDB
------------

| Database | What it holds | Writers | Readers | Cleared |
|---|---|---|---|---|
| `…library` | The library's document, a document per feed, and the search index's | The tabs, with `library/durable-swap!`; the worker puts only feed documents | The tabs, with `library/read!`; the worker, with `fetch-due!` | Never by the app; the browser can evict it unless the site's storage persists |
| `…cache` | Directory results, feed URLs by guid, feeds of shows outside the library, linked shows, colours of artwork | `searches`, `feeds`, `artwork-colors`, once the page is idle | the same | By age and count, and Clear cache |


Between the tabs and the worker
-------------------------------

| Place | What it carries | Writer | Readers |
|---|---|---|---|
| The library's channel | The version of the library that a tab wrote | `library/announce!` | `library`, which reads what's new |
| The player's channel | The playing tab's media state, a new tab's question, and commands for the playing tab | `audio` | `audio` |
| The play lock, a Web Lock | Which tab's element plays; a tab that plays takes it from the others | play-nice's `tabs/attach!`, through `runtime/attach-player!` | play-nice, which pauses the element that lost it |
| The refresh lock, a Web Lock | That a worker refreshes, so one does at a time | `fetching`, in the worker | `fetching`, which answers that it's busy |
| The worker's messages | What a refresh or a fetch asks for, and what it found, in EDN | `fetching` | `fetching` |


In the page
-----------

| Place | What it holds | Writer | Readers |
|---|---|---|---|
| The audio element | The episode's file, where it is, the volume and the rate | `audio`, through play-nice | `audio`, through `runtime/attach-player!` |
| The Media Session | The episode on the lock screen, and its buttons | `runtime/lock-screen!`, from `audio` | The platform |
| `<html>`'s `data-theme` and `data-contrast`, the `theme-color` and the title | The look of the page | `page`, at each render | CSS, the browser |
| The history | The page's URL; `page/canonicalize!` changes it without a navigation, so `:route` keeps the URL that the link gave | `routes`, `page` | `runtime/follow-history!` |
| The listeners | The page shown, hidden or restored, the end of a transition, the channels' messages and the store's changes | `runtime`, for `main`, `library`, `audio` and `opml` | — |


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
