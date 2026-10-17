Podcast app
===========

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
- **Layout first.** The CSS solves the layout and nothing more. The look of
  the app comes later.
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
- **Fix the libraries at the source.** When podcast-clj, play-nice or
  another of my libraries is buggy or confusing, fix it there rather than
  work around it in the app.
- **Point out what's generic.** Code that would be useful beyond the app is
  a candidate for a library of its own.


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

The `:local` alias takes podcast-clj and play-nice from the checkouts beside
this one, `../podcasting` and `../play-nice`, so that a fix in either shows
in the app at once. Without it, both come from the commits in `deps.edn`.

The watch starts an nREPL server, whose port is in `.shadow-cljs/nrepl.port`.
Evaluate `(shadow.cljs.devtools.api/repl :app)` there to reach the app in an
open tab. The `:serve` alias starts another one, whose port is in
`.nrepl-port`.

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
