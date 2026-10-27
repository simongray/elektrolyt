Elektrolyt
==========

A podcast app for the browser, built on
[podcast-clj](https://github.com/simongray/podcast-clj) and
[play-nice](https://github.com/simongray/play-nice), and rendered with
[Replicant](https://replicant.fun). The app runs in the browser, and a small
server on the JVM serves it, together with a proxy for its requests and
thumbnails of artwork.


Running the app
---------------

You need Java, the [Clojure CLI](https://clojure.org/guides/install_clojure)
and Node with npm. CI uses Java 25 and Node 24. Install the npm packages
once:

```bash
npm install
```

Then start the server and the shadow-cljs watch in one JVM:

```bash
clojure -M:cljs:server:serve
```

If you have checkouts of the libraries beside this one, add the `:local`
alias to this command and to every other one, e.g.
`clojure -M:cljs:server:local:serve`. It takes podcast-clj, play-nice,
colour-coded, like-minded, mind-meld, wary-fetch and drop-in-search from
`../podcast-clj` and the other checkouts, so that a fix in any of them
shows in the app at once. Without it, they come from the versions in `deps.edn`.

The app is at http://localhost:8400, and its views are at
http://localhost:8402, in [Portfolio](https://github.com/cjohansen/portfolio),
with a scene for each state of each view.

To listen with the app as a listener gets it, call `(listen!)` in the
JVM's REPL. It builds a release into `target/release` and serves it at the
same address, so it opens the same library, and a tab gets it when it
reloads. Your edits don't show there until you call it again, and
`(develop!)` serves the watch's build once more. To profile a release,
`(listen! timed)` builds one that marks its work in the Performance panel
of the browser's DevTools, as the dev build does, and keeps readable
names. `test/e2e/bench/bench.mjs` times the start and a subscription with
a library of 50 shows.

The server listens on localhost alone, since it has no login yet and its
proxy fetches any public URL for whoever reaches it. It also makes small
thumbnails of artwork at `/artwork`, since most feeds name a single artwork
of 3000 pixels, and keeps them in `.cache/artwork`, at most 200 MB.

To search the Podcast Index too, and to find a show by its guid alone, put
the [credentials of the Podcast Index](https://api.podcastindex.org) in an
EDN file of secrets, as `{:podcastindex {:key "…" :secret "…"}}`. The
server signs the requests to the Podcast Index with them, so the secret
never reaches the page. Name the file in `PODCAST_APP_SECRETS`, or put it at
`../secrets.edn`, beside the checkout.


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


The code
--------

The app is in `src/dk/simongray/elektrolyt/`, the Portfolio scenes are in
`dev/`, and the tests are in `test/`. The namespaces are grouped by what
they're about, so that a new one has an obvious place:

- **Where the app runs.** The root namespace, `dk.simongray.elektrolyt`,
  starts the app in the page, `worker` starts its web worker, and `server`
  is the server.
- **The listener's library,** in `library`: the library on this device,
  the changes to it, its refresh and OPML, the Library, Queue, Inbox and
  Settings pages, and the picks.
- **Playback,** in `player`.
- **The Show and Episode pages,** in `show` and `episode`.
- **Search,** in `search`, which asks the directories and drpodcast.nu.
- **What every page shares,** in `ui`: the page around the views, Home,
  the views that several pages use, the routes, the app's words and
  colours, and the artwork.
- **What reaches outside the store,** in `runtime`: the listeners, timers,
  channels, localStorage, caches and logs.

Within a group, a subject's model, effects and views sit together, e.g.
`library.queue` is the queue's model, `library.queue.ui` its views, and
`player.audio` the player's effects. A new feature goes into the group it
belongs to, e.g. playlists into `library` or comments into `episode`. A new
group is only for a new domain, as podcast-clj has `value` for payments.

A test checks that only the namespaces marked as runtime use listeners,
timers, locks, channels or localStorage, so that they stay few.
[STATE.md](STATE.md) lists every place where the app keeps something.


Development
-----------

To work from an editor, start a REPL with the `:cljs` and `:server` aliases
rather than `:serve`, and evaluate `(start!)` in the `user` namespace. After
you reload the server's namespace, `(restart!)` starts it again with the new
handlers.

The watch starts an nREPL server of its own, whose port is in
`.shadow-cljs/nrepl.port`. Evaluate `(shadow.cljs.devtools.api/repl :app)`
there to reach the app in an open tab. The `:serve` alias starts another
one, whose port is in `.nrepl-port`.

The page, its web worker and the server log with
[Telemere](https://github.com/taoensso/telemere). Each event has an id and
data, e.g. `:library/save-failed`, and a release build logs only errors and
warnings.

The icons are the Light weight of [Phosphor Icons](https://phosphoricons.com),
from the npm package `@phosphor-icons/core`, with the Fill and Thin weights
for the few that need them, e.g. `bookmark-simple-fill` for a subscription.
Each icon's path is a def of its own in the `ui.icons` namespace, which holds
only the icons that the code uses and starts with Phosphor's licence, MIT. To
use another icon, write `icons/<name>` and evaluate
`(write-icons! "light" "fill" "thin")` in the
`dk.simongray.elektrolyt.phosphor` namespace, and do the same after you update
the package.


Tests
-----

To run the tests in Node:

```bash
clojure -M:cljs compile test
```

While the watch is running, don't start a second shadow-cljs beside it.
Evaluate `(shadow.cljs.devtools.api/compile :test)` in its nREPL instead,
which runs the tests too.

To run the server's tests on the JVM:

```bash
clojure -X:server:test
```

The end-to-end tests run in Firefox and your installed Chrome, against the
app in `public/`, with a server of their own on a free port. The watch
builds the app there, so start the watch first, or else build the app
with `clojure -M:cljs compile app`. Install Playwright's Firefox once:

```bash
npx playwright install firefox
```

Then run them. Their server takes the `:server` and `:local` aliases unless
`E2E_ALIASES` names others, so with the checkouts, `npm run e2e` is enough.
Without them, name `:server` alone:

```bash
E2E_ALIASES=:server npm run e2e
```

To run them against a release, as CI does, build one with `(release!)` in
the watch's nREPL, or `clojure -M:cljs clj-run user/release!` without the
watch, and set `E2E_RELEASE=1`.

`npm run perf` checks the key flows against their budgets: a show of 2,500
episodes opened, subscribed to, left for the Library and back, shown in
full, played and checked for new episodes, in Chrome at a quarter of the
CPU's speed. A flow's longest frame must stay under its budget in
`test/e2e/perf-budgets.json`, which `PERF_CALIBRATE=1 npm run perf`
measures over five runs and writes. The budgets are a release's, so run
it with `E2E_RELEASE=1`. CI's runners are slower, and keep budgets of
their own in `test/e2e/perf-budgets-ci.json`, which CI measures and
prints when the file is missing.

To check the size of the release build, evaluate `(sizes/check!)` in the
watch's nREPL, or run `clojure -M:cljs clj-run sizes/check!` without the
watch. It builds a release into a snapshot of its own, prints each
namespace's share of it and writes shadow-cljs's report to
`target/build-report.html`. It fails where a module is bigger than its
budget in `dev/sizes.edn`, raw or gzipped, so raise a budget only for a
dependency you add on purpose.

CI runs all four, the end-to-end tests against a release, and lints
`src`, `test` and `dev` with clj-kondo first. A job of its own checks the
performance budgets, without failing the run until they're calibrated on
CI's machines.

To time the app with a library the size of a listener's, there's a
benchmark of 50 shows from podcast-clj's sweep corpus, 27,575 episodes in
all. It serves the corpus's feeds to the app's proxy in place of the
network, so each run is the same. Seed its profile once, with the watch
running, and then time opening Home or subscribing to a show:

```bash
node test/e2e/bench/bench.mjs seed
node test/e2e/bench/bench.mjs open 5
```

It takes the corpus from a podcast-clj checkout beside this one, unless
`BENCH_CORPUS` names another folder, and the app from port 8400, unless
`BENCH_URL` names another. `BENCH_CPU=4` slows the CPU down four times,
and `BENCH_PROFILE` names the folder of another library to keep. Its `shows.opml` imports the same shows with
their real feeds too, e.g. to profile the app in your own browser.

Every build marks the stages of the page's start on its timeline, e.g.
`startup/library` once the library is read, and `bench.mjs open` prints
them. The dev build also measures each render, dispatch and library
operation, which DevTools' Performance panel shows on a track named
Elektrolyt, and logs a render over 16 ms as `:perf/slow-render`. A release
built with `dk.simongray.elektrolyt.runtime.perf/enabled?` set in its
`:closure-defines` measures them too.
