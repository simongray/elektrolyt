Podcast app
===========

A podcast app for the browser, built on
[podcast-clj](https://github.com/simongray/podcast-clj) and
[play-nice](https://github.com/simongray/play-nice), and rendered with
[Replicant](https://replicant.fun).


Development
-----------

Install the npm packages once, then start the watch:

```bash
npm install
clojure -M:cljs:local watch app portfolio
```

- The app is at http://localhost:8401.
- The views are at http://localhost:8402, in
  [Portfolio](https://github.com/cjohansen/portfolio), with a scene for each
  state of each view. The scenes live in the `-scenes` namespaces of `dev/`.

The `:local` alias takes podcast-clj and play-nice from the checkouts beside
this one, `../podcasting` and `../play-nice`, so that a fix in either shows
in the app at once. Without it, both come from the commits in `deps.edn`.

The watch starts an nREPL server, whose port is in `.shadow-cljs/nrepl.port`.
Evaluate `(shadow.cljs.devtools.api/repl :app)` there to reach the app in an
open tab.

To run the tests in Node:

```bash
clojure -M:cljs:local compile test
```
