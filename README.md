Elektrolyt
==========

A podcast app for the browser, built on
[podcast-clj](https://github.com/simongray/podcast-clj) and
[play-nice](https://github.com/simongray/play-nice), and rendered with
[Replicant](https://replicant.fun).


Principles
----------

- **Semantic HTML.** Each kind of content has the element made for it, and
  each page has landmarks that describe its parts.
- **Accessible from the start.** Every view works with a keyboard and a
  screen reader, and so do the scripts behind it.
- **Responsive.** Every view works on a phone and on a desktop, down to a
  width of 320 CSS pixels.
- **Layout apart from look.** The CSS keeps how things are laid out in one
  part and how they look in another, so that either can change without the
  other. The colours come from tokens, defined once for light and dark,
  which take their hue from the artwork.
- **A thick client and a thin server.** The browser keeps as much as is
  practical, and the server does only what a browser can't. The app is for
  one listener on a server of their own, but nothing should stand in the
  way of several.
- **Ready to sync.** The listener's data is kept so that other web and
  mobile apps can sync with it later.
- **Replicant as it's meant to be used.** The views are pure functions of
  the state, and their event handlers are data. The pages render in the
  browser alone, with no rendering on the server.


How to work on the app
----------------------

- **Shape each part before you build it.** The UI grows one part at a time,
  and each part is discussed until its design is settled.
- **Research first, and cite it.** Base a new feature or UI pattern on
  research into how it's done well, and cite the sources in a comment where
  the code makes the decision.
- **Fix the libraries at the source, when it's a fix.** When the fix is a
  bug, a pure function or a platform edge of podcast-clj, play-nice or
  another of my libraries, fix it there rather than work around it in the
  app. Build coordination, persistence and policy in the app first, and
  move them to a library once a second app needs them.
- **Point out what's generic.** Code that would be useful beyond the app is
  a candidate for a library of its own.


Namespaces
----------

The namespaces are grouped by what they're about, so that a new one has
an obvious place:

- **Where the app runs.** The root namespace, `dk.simongray.elektrolyt`,
  starts the app in the page, `worker` starts its web worker, and `server`
  is the app's server, on the JVM.
- **What the listener keeps,** in `library`: the library on this device,
  its operations, refresh and OPML, and the Library page, the queue, the
  inbox, the picks and the settings.
- **What plays,** in `player`.
- **The pages of a show and of an episode,** in `show` and `episode`.
- **The search for shows,** in `search`, which asks the directories and
  drpodcast.nu.
- **What every page shares,** in `ui`: the page around the views, Home,
  the views that several pages use, the paths of the pages, their words
  and colours, and the artwork.
- **What reaches outside the store,** in `runtime`: the listeners, timers,
  channels, localStorage, the caches and the logs.

Within a group, a subject's model, effects and views sit together, e.g.
`library.queue` holds what the state keeps of the queue, `library.queue.ui`
its views, and `player.audio` the player's effects, which the root
namespace registers. A new feature goes into the group it belongs to, e.g.
playlists into `library` or comments into `episode`, and a new group is
only for a new domain, as podcast-clj has `value` for payments.

A test checks that every namespace that uses listeners, timers, locks,
channels or localStorage is marked as runtime, so that they stay few, and
[STATE.md](STATE.md) lists every place where the app keeps something.


Development
-----------

Install the npm packages once, then start the app's server and the watch in
one JVM:

```bash
npm install
clojure -M:cljs:server:local:serve
```

- The app is at http://localhost:8400, together with the proxy through
  which it fetches feeds. The server listens on localhost alone, since it
  has no login yet and the proxy fetches any public URL for whoever
  reaches it.
- The server also makes small thumbnails of artwork at `/artwork`, since
  most feeds name a single artwork of 3000 pixels. It keeps them in
  `.cache/artwork`, at most 200 MB.
- The page, its web worker and the server log with
  [Telemere](https://github.com/taoensso/telemere). Each event has an id
  and data, e.g. `:library/save-failed`, and a release build logs errors
  and warnings alone.
- The views are at http://localhost:8402, in
  [Portfolio](https://github.com/cjohansen/portfolio), with a scene for each
  state of each view. The scenes live in the `-scenes` namespaces of `dev/`.

To search the Podcast Index too, and to find a show by its guid alone, put
the [credentials of the Podcast Index](https://api.podcastindex.org) in an
EDN file of secrets, as `{:podcastindex {:key "…" :secret "…"}}`. The
server signs the requests to the Podcast Index with them, so the secret
never reaches the page. In development, the file is `../secrets.edn`,
beside the checkouts. Otherwise, name it in `PODCAST_APP_SECRETS`.

From an editor, start a REPL with the `:cljs`, `:server` and `:local`
aliases instead and evaluate `(start!)` in the `user` namespace. After you
reload the server's namespace, `(restart!)` starts it again with the new
handlers.

The `:local` alias takes podcast-clj, play-nice and colour-coded from the
checkouts beside this one, `../podcast-clj`, `../play-nice` and
`../colour-coded`, and like-minded, mind-meld and wary-fetch below
podcast-clj too, so that a fix in any of them shows in the app at once.
Without it, they come from the versions in `deps.edn`.

The watch starts an nREPL server, whose port is in `.shadow-cljs/nrepl.port`.
Evaluate `(shadow.cljs.devtools.api/repl :app)` there to reach the app in an
open tab. The `:serve` alias starts another one, whose port is in
`.nrepl-port`.

The icons are the Light weight of [Phosphor Icons](https://phosphoricons.com),
from the npm package `@phosphor-icons/core`. Each icon's path is a def of its
own in the `ui.icons` namespace, named as Phosphor names it, so a release
build keeps only the icons that the views use. After you update the package,
evaluate `(write-icons! "light")` in the `dk.simongray.elektrolyt.phosphor`
namespace to write them again. Phosphor's licence, MIT, is at the top of that
file.

To run the tests in Node:

```bash
clojure -M:cljs:local compile test
```

To run the server's tests on the JVM:

```bash
clojure -X:server:local:test
```

To run the end-to-end tests in Firefox and your installed Chrome, install
Playwright's Firefox once, and then run them against the app that the watch
built into `public/`. They start a server of their own on a free port.

```bash
npx playwright install firefox
npm run e2e
```
