Podcast app
===========

A podcast app for the browser, built on
[podcast-clj](https://github.com/simongray/podcast-clj) and
[play-nice](https://github.com/simongray/play-nice), and rendered with
[Replicant](https://replicant.fun).


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
