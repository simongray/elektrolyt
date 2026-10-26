# Making Elektrolyt's frontend smooth

*2026-10-25, for Simon Gray.*

This report ranks what would make the app feel smoother to a listener: fewer
freezes and blank screens, shorter waits, steadier pages, and the measurements
that tell which of those matter. Researchers each took one area (rendering, the
main thread, perceived speed, the network, loading, startup, motion and
measurement) and proposed fixes. A skeptic then checked every idea against the
code, corrected its evidence and estimates, merged duplicates and dropped what
didn't hold.


## How to read it

- **Gain** (1–5) is how much smoother the app feels to a listener. A 5 removes
  a freeze, blank screen or seconds of waiting that most listeners hit every
  day; 4 makes a common path noticeably smoother; 3 is a visible improvement on
  a common path, or a big one on an occasional path; 2 is polish; 1 is barely
  noticeable. An unmeasured guess gets a lower gain and a lower confidence.
- **Hours** are focused hours for you and Claude, with the research comments,
  unit and end-to-end tests, Portfolio scenes and accessibility checks that
  every change here gets.
- **Speed** comes from the hours: 5 up to 1 h, 4 up to 4 h, 3 up to 8 h, 2 up
  to 24 h and 1 beyond.
- **Score** is gain × speed. Ties go to fewer hours, then higher confidence. An
  item that needs another sits just below it, whatever its own score, and says
  so.
- Measurement items have a gain of 1 or 2, since they smooth nothing by
  themselves, so they rank low, and the items that wait for them follow them
  down.
- **Status** says which items are done, only measured, planned, deferred,
  left for later or declined by you, and "Where it stands" gives the measurements
  and reasons behind each.
- Paths start below each project's `src/dk/simongray/` folder, e.g.
  `elektrolyt/show.cljc:133` in the app, `podcast/app/library.cljc:2182` in
  podcast-clj and `drop_in_search/indexing.cljc:79` in drop-in-search, and
  `elektrolyt.cljs` is the root namespace. Paths that start with `public/`,
  `test/`, `dev/` or `.github/` are at the app's root. Replicant's are in the
  fork at `~/.gitlibs/libs/no.cjohansen/replicant/ec3cdd2…/src/`, and other
  jars' paths name their version.


## Where it stands

Measured on 26 October 2026 in the dev build, in headless Chrome, with a
stand-in library of 53 shows, every seventh of `doc/coverage-2026-shows.csv`,
unless a line says otherwise. A release build was about a tenth faster.

Home took 6.5 s to appear, and 5.5 s in a release build. Almost all of it was
one task that read the library, and most of that read the saved search index.
Home now shows its frame after 0.3 s and the page after 2.6 s. Of what's left,
reading the index takes 1.6 s: 0.8 s to decode the postings, with no single
hotspot, 0.5 s to build the segments and 0.16 s to read the ids as EDN.
Reading the feeds takes about 0.3 s.

Done, with the commit of each:

- Before the ranking, subscribing waited for `runtime/painted!`, so that the
  bookmark fills before the work (13f2ac6), and the web worker built a
  subscription's search documents, which cut subscribing on the page from a
  median of 515 ms to 128 ms (ad2844a, podcast-clj b8f4aab).
- #1 (cad3272): Home looks at the subscriptions rather than count every
  show's episodes.
- #2 (c9381e0): what a refresh found comes in the library's codec. A feed of
  27 MB took 2527 ms to read as EDN, against 38 ms to decode.
- #3 (af8ac9c): the player's store keeps only what the page reads. An
  earlier commit, 83859eb, had already brought playback down to one change a
  second.
- #4 (beb8957): a subscribed show's page is made again only when its feed,
  subscription or settings change. It was made at every position report,
  71 ms for a show of 4574 episodes.
- #5 (3a43571): a ring around Pause while the episode waits for data.
- #6 (441c099): Back and Forward scroll main to where the page was left.
- #7 (podcast-clj 29774bb): a refresh builds the search documents of the
  feeds that changed in the worker. For a feed of 4576 episodes, that took
  the page 2209 ms.
- #8, its measurement only (4296ec3): a release builds into target/release,
  and opened Home in 5.5 s against 6.5 s.
- #9 (31ed18c): main stays as it was while only the player's clock ticks. A
  tick on the Library page renders in 7 ms rather than 22.
- #85, in #10's place (df2923c): the page's frame renders at once, after
  0.3 s.
- #11 (drop-in-search 60e6eb8): what is stored of a document stays EDN text
  until something asks for it. Home appears after 2.60 s rather than 2.99.
- #13 (podcast-clj c7aa58f, 865e570): a refresh applies the feeds that
  changed in parts of 500 episodes. After an import of the 53 shows, one task
  of 7.7 s became tasks of 0.9 s at most.
- #14 (d13b93b): the frame after Subscribe came 83 ms after the click, and
  now 55 ms.
- #15 (a4c82ae): work at idle waits for the paint where there's no
  requestIdleCallback. It isn't tried in Safari yet.
- #16 (f160391): one Intl formatter of each kind for each language. A date
  takes 2 µs rather than 41.
- #17 (b75bed7): the last four show and episode pages are kept. Going back
  to a show of 4576 episodes took 92 ms, and now nothing.
- #19 (5467808): a subscription shares the Show page's fetch of the whole
  feed in flight.
- #20 (podcast-clj deebd62): Apple's artwork comes in WebP at 64 to 512 px
  too. A result of 64 px at 2x loads 6 kB rather than the JPEG of 80 kB.
- #21 (51661b7): Charis Bold is preloaded, and the fonts are kept for a year
  in a folder named for Charis 7.000.
- #23 (126e30b): a Show or Episode page's artwork shows the small copy that
  a row or a result loaded until the large one arrives.
- #24 (4268684): a show opened from a podroll or publisher card shows the
  card's title and artwork at once.
- #25 (00d4598): the lock screen's Next and Previous follow a change of the
  choice of track buttons, in this tab or another.
- #26 (fba524b): a show's subscription is looked up once until the
  subscriptions change: 0.006 ms rather than 0.24 a time with 57 of them.
- #27 (0302830): a result's or a card's artwork, and a focus that rests on a
  title, fetch the start of a show's feed ahead, as a hover on a title did.
- #28 (b14e34e): drpodcast.nu's slugs.json comes through the HTTP cache, and
  DR's answer for each series is kept for a day. Two overlapping searches
  asked DR about 12 series, and then 3.
- #29 (f198811): the lock screen gets the server's thumbnails of 256 and
  512 px rather than the original artwork.
- #30 (88df8de): a row's thumbnails, slug and short hash are kept for the
  last 2,000 strings. An Inbox of 99 episodes renders in 57–62 ms rather
  than 75–76.
- #31 (14d8932): a Show page makes its rows only as far as it lists them. A
  show of 4575 episodes makes its page in 19–20 ms rather than 41–44. The
  sort in podcast-clj's `resolve/episodes` stays as it is: it takes 15 ms
  for those episodes, and working out each key once would save about 3,
  since a feed's items usually come in order.
- #32 (0fd1dd8): the server fetches four artworks at a time and scales two.
  A cold grid of 54 artworks fills in about 3.1 s rather than 4.3–5.4.
- #33, measured in the app rather than in podcast-clj's bench: with its
  documents built in the worker, which takes 2.2 s, subscribing to a show of
  4575 episodes takes the page 1.1 s in the library of 53 shows, and 0.38 s
  in an empty one. The difference is drop-in-search merging the index's
  segments, four into two, which #41 targets. Without built documents,
  subscribing takes 3.6 s, and unsubscribing takes 40 ms.
- #34 (2d1adf6): Unsubscribe empties the bookmark before the library lets
  the show go, and the page keeps the episodes from the library's copy
  while it checks the feed again. The click's frame comes after 16 ms
  rather than 120, and the page no longer says "Loading the show…" for
  about 3 s.
- #35 (d17234d): a short line moves under the status of a load that lasts
  past a second, four times at most.
- #36 (like-minded 2359a21, 8c6bb05): the artwork colours read at start
  come from one listing of the cache. With a full cache of 1,220 entries,
  the Library page's 56 colours take 11–17 ms rather than 351–378.
- #37 (podcast-clj 81a7585, 0ab5f17): an outside show's picks work out the
  runs that its notes repeat once per feed, so the picks of a show of 1,200
  episodes are made again in 115–194 ms rather than 347–634. In the worker,
  as the item planned, the runs held up every Show page's whole feed by
  0.55 s, though More like this starts folded.
- #38 (98ab7e8): rows in the place of a show's episodes, and lines in the
  place of an episode's notes, while a feed loads. They show after a
  second, like the skeleton as the app starts, rather than the item's
  300 ms.
- #39, its first stage (podcast-clj 14908cb): a refresh builds each changed
  feed's documents in a task of its own, so a listener's request to the
  worker waits for one feed at most. That's 139 ms for a show of 347
  episodes, but 2.2 s for one of 4576. The second stage, a worker for the
  listener's requests, waits for a trace that needs it.
- #40, its lazy artwork (fb6a66b): the Library's tiles past the first dozen
  load their artwork lazily, so a fresh Library page of 54 shows asks for
  16 thumbnails rather than 54. Its content-visibility is left out: the 54
  tiles lay out again in 1.8 ms, which doesn't pay for the risk to focus
  rings and to scrolling in Safari.
- #41 (drop-in-search 94dcb5d): an add's merges stop at 8192 documents.
  Adding 4577 documents to 22,500 that 50 adds made takes 272–283 ms rather
  than 530–579 on the JVM, and queries over the extra segments give the
  same results in 0.57 ms rather than 0.30. The bound on pruning is left
  out.
- #42 (drop-in-search e36838c, podcast-clj 632108f): a tab that catches up
  with another tab's write reads only the segments it lacks. After another
  tab's subscription, that takes 97 ms rather than 2196.
- #43 (ce28a20): a benchmark, `test/e2e/bench/bench.mjs`, times the app
  with 50 shows of podcast-clj's sweep corpus, 27,575 episodes, served
  offline. Home appeared after 2.27–2.37 s, and subscribing to Garage Logic
  had a longest task of 443–458 ms.
- #45 (0e147df): the picks are found after the page paints and kept in the
  store by place, so a change of the queue on Home renders in 4–9 ms.
- #46 (fc8b0a2, podcast-clj e412da5): the worker keeps only the notes that
  the app reads. The feeds encode to 90 million characters rather than 140,
  and decode in 262 ms rather than 380. Only a feed that changes is written
  leaned, though: one whose server answers 304 kept its stored copy, so 29
  of the 54 feeds in the in-app browser's stand-in library held the notes
  twice, until the library was leaned once at the start (below).
- #48, its first stage (bfa1c93): an episode's other files are read once
  it plays steadily rather than in the Play click, and a restored episode's
  at idle.
- #49, its first part (5db7027): Play skips the add of an episode that the
  library has, so a click's processing took 21 ms rather than 36.
- #50 (eba33db): index.html preloads the three stylesheets that app.css
  imports.
- #51 (617c072): the people of a show or an episode show as the server's
  thumbnails of 48 px.
- #52 (e0755e1): a render of the same state as the last is skipped, the
  title is set only when it changes, and the theme colour is read once
  after the paint.
- #54 (4303e8d): the server hashes each file once per version, by its
  modification time, its length and its ctime.
- #56 (6753cef): the web worker starts at idle once a show's start is
  fetched ahead, rather than at the first request, which waited 257–287 ms
  for a cold worker.
- #57 (3915b8d): the e2e tests run with reduced motion, and a unit test
  checks that the CSS gates every transition and animation.
- #58 (2dda6df), #60 (b40d090), #61 (c2d0c4f) and #62 (1ca871a): the
  bookmark drops in and pulses three times at most while a subscription
  works, a dialog eases in, a jump from Search's row glides to a
  directory's list, and the button for new episodes takes the status
  line's place, so the list moves less than 8 px rather than 36.
- #63 (f24aea2): the first refresh starts at idle once the first page has
  painted.
- #64 (8659b31): evaluating `(sizes/check!)` builds a release snapshot,
  writes shadow-cljs's report and fails over the budgets in
  `dev/sizes.edn`, and CI runs it too. The page is 1.97 MB of script, 531
  kB gzipped, and its largest parts are cljs.core, whatwg-url, xmldom,
  podcast-clj's diagnostics, webvtt-clj and feed.spec.
- #65 (f3dffb5): the proxy and the thumbnails send their waits as
  Server-Timing. A thumbnail of 1,006 ms was 907 ms of fetching and 70 of
  scaling, and three others waited about 210 ms for another request's
  fetch of the same artwork.
- #66 (a06510e): the caret, the switch's knob, the volume panel and hovered
  artwork ease, while the volume panel still shows at once and hides at
  once on Escape.
- #67 (9888f6b): a fetched feed is cached as the string that the worker
  sent, which skips an encode of 23 ms for a feed of 3.7 million
  characters, and of 127 ms for 20.8 million.
- #68 (bfc4fa6): the icons' namespace holds only the 34 icons that the code
  uses, which takes the dev build's main.js from about 3.4 MB to 1.3 MB.
- #69 (2eb1a85): the worker reaches none of the page's namespaces, which a
  JVM test checks. That moved 34.7 kB, 9.7 kB gzipped, from shared.js to
  main.js.
- #71 (436be31): User Timing marks each stage from a Play to the element
  playing, and `:player/started` logs them. On a phone, read the log
  through remote debugging of the dev build.
- #73, measured (17980ae): with `bench.mjs colours`, the 0.6 s transition
  of the seeds made no frame over 20 ms at 1x and 4x CPU throttling, and
  one at 6x, as changing them at once did, so it stays.
- #78 (f4cb654): the Library's artwork moves as Show as or Sort by changes,
  and a change of light or dark or of the contrast cross-fades the page.
  Midway, the artwork had the colour it has after, so the multiply holds.
- #79 (8ecc247), #82 (8a2bf69), #84 (14a0661) and #87 (e6789a7): the
  runtime.perf namespace measures the renders, the dispatches, the
  library's operations and the codec on a DevTools track in dev, marks the
  stages of the start in every build, and logs slow interactions and long
  frames, keeping the worst for `report`. With 53 shows, IndexedDB took
  150–200 ms and the decoding after it 1.7 s. At 4x throttling, the
  library's read blocked one frame for 1.8 s, and a click into the Library
  took 656 ms.
- #80 (10da984): with an hour's transcript open, a render took 14 ms more,
  and now takes 0.8–1.7 ms, as with it closed.
- #81 (podcast-clj 4ffb8ba, 210a78f): a screen of shows keeps what it
  takes of each feed, and counts the statuses from the episodes that the
  profile holds marks of. For 54 shows of 27,831 episodes, it's made again
  in 4.9 ms rather than 91 after each change of the profile, e.g. each
  report of a position while the Library shows. An item is found by its
  guid in an index of its feed, which takes 0.002 ms rather than 0.59 for
  an episode 700 items in. A queue of 50 episodes still takes about 8 ms,
  almost all of it each row's view, which is under a frame. Both caches are
  weak, so they go with their feed. The shows' counts are also worked out
  at idle after the start (ff8eee0), so the first click into the Library
  takes 46–60 ms rather than 115.
- #93, measured, and #94 (6eac94d): ten outside shows held about 110 MB of
  heap, and none of it went. Besides the store, two leaks held them:
  `memo/remember-recent` dropped its oldest result with a subvec, which
  kept every result it had made (e2a4a0d), and drop-in-search's merges
  kept the segments they replaced beneath the index's (31b74e4). With
  those fixed and the store keeping five feeds, the heap on Home after ten
  outside shows is 59.5 MB rather than 141.6. The library of 53 shows holds
  274 MB once started. An hour of play isn't measured.
- #95, measured (1e15f47): `bench/startup.mjs` traces a release's load. On
  a hot load with an empty library at 4x throttling, shared.js evaluates in
  265 ms and main.js, with init, in 290 ms, and V8 compiles them off the
  main thread. A CPU profile puts most of it in cljs.core and V8 itself,
  and no namespace of the app's in more than a few milliseconds, so nothing
  is worth splitting off by the 50 ms rule.

Two costs that the ranking lacked, both fixed:

- In podcast-clj, `library/urls` was a map keyed by whole feeds, so building
  it hashed every feed: 800 ms in Home's first picks (podcast-clj 739822f).
- As drop-in-search read the index, it built a vector of pairs for every
  posting in a field without positions: 1.3 s. Reading the index went from
  4.0 s to 1.9 s (drop-in-search 375f0de).

Deferred, by what was measured:

- #12: after #9, a Play click renders 7 times in 35 ms in all, and Pause 4
  times in 11 ms. A render per frame would save about 25 ms a click, and
  every read of the page after a change of the state would have to render
  first.
- #18: during a refresh of the 53 shows, the page's own request through the
  proxy took 188–558 ms, against 192–380 ms when nothing else ran. A cap of 3
  feeds in flight would slow every refresh by about a third.
- #22: after Play next, the frame came 14–15 ms after the click, with no long
  task, so the save doesn't hold up a click's frame.
- #10, for later: after #85, the page is blank only while its scripts run,
  0.3 s in dev. It's worth doing if a phone shows that gap.
- The rest of #8: a release now opens Home only about 0.35 s sooner.
- #44: a render while only the clock ticks takes 0.30 ms, and the lock
  screen's case needs playback with sound to measure.
- #47, with #18: the contention it targets measured small.
- #53: WebKit fetched /api/server twice with a preload.
- #76: the Library's content for 54 shows builds in 2.7 ms and its sort
  takes 0.07 ms, so a memo and aliases would save a few milliseconds a
  render.
- #86: IndexedDB's reads span 150–200 ms, so decoding the feeds as they
  arrive saves at most that of 2.2 s, and it reaches into `read!`'s rules
  for retries, beside your own work in podcast-clj.
- #96: of the tables built at load, only podcast-clj's zone for UTC
  showed, 32 ms at 4x, as its first Intl formatter with a time zone loads
  the zone data, which formatting any date loads anyway.
- #92: in Chrome 152, each artwork's multiply makes a layer of its own, 69
  for 61 images against 7 without it, and isolating main leaves 69, so the
  fix doesn't work. The multiply on the intro's artwork alone would change
  the look.

Later, with the item they need:

- #74 with #10, #83, #88, #97 and #100 with #8's release on the same
  origin, #55, #90 and #91 with runs on phones, and #59, which is polish.
  #98 isn't started, and #99's third stage, building and encoding
  segments in the worker, waits as
  [search-index-plan.md](search-index-plan.md) has it.

Your calls, made on 26 October:

- #70, declined for now: a cap drops episodes that a host no longer serves
  and the listener never touched, while Apple Podcasts and Pocket Casts keep
  all they've seen. Neither library measured here is old enough to have
  kept many, so what a cap would save is unknown.
- #72 and #75, both declined: either is barely noticeable, and while
  something fades, its artwork's multiply switches off and snaps back at
  the end. If motion is wanted there after all, #72 is the one.
- #77, declined: with #85, the frame shows after 0.3 s and Home after about
  1.85 s. A snapshot would fill only the page's content in that time, with
  a copy that ignores taps and can jump when the page replaces it, and it
  keeps every fact on the page a second time, outside the state.
- #89, declined: it reads first only the feeds that a page needs, but most
  of the read is the search index, which the picks on Home, Show and
  Episode pages need too. The feeds took about 0.3 s of it before the
  leaning, so a deep link would gain a few hundred milliseconds, for a
  partly loaded state and the risk that a missed path cuts unloaded feeds
  to their URLs, whose documents the next save deletes. A big library's
  start is in the index, for #99 or a change of its format.
- #97, later, by its second way: keep the names that shadow-cljs gives, and
  have a release step copy each file to a name hashed by its content and
  rewrite the importScripts string in worker.js, so that no code ships
  twice. It waits for #8's release on the same origin, and it goes if #100
  is built, whose versioned cache does the same.

New, and not in the ranking: the save after an import of the 53 shows is one
task of 3.7 s, 3.2 s of it in drop-in-search's `ciff/segment-body`, which
writes the index's segments. #22 only moves it after the paint.

Also new, and fixed: drop-in-search read each document's id as EDN at the
start. A vector of plain strings, as the app's ids are, is now split at its
quotes (drop-in-search 9ca9ed8), so decoding the library of 53 shows takes
1,460–1,469 ms rather than 1,622–1,689, and Home appears at 1.95–1.99 s
rather than 2.13–2.25. The rest of the read is the index's postings, about
600 ms with no single hotspot, which only a change of its format or #99
would cut.

Also new, and fixed: the heap after the start held the notes of many
episodes twice, as `:content/encoded` and `:rss/description`, since #46's
leaning reached only the feeds that changed. In the in-app browser's
stand-in library that was 12,984 of 27,833 episodes, in 29 of 54 feeds,
and 13.9 million characters. Now the app leans the library's feeds once at
idle after the start, with podcast-clj's `library/leaned` (8938822), and saves it,
which writes those feeds' documents anew and deletes the ones before
(e7492ae). A lean library stays as it is, so it happens once. With the 53
shows of the benchmark, whose feeds were all from before #46, the heap
after the start is 173–176 MB rather than 268, IndexedDB's reads take
118–134 ms rather than 149–165, decoding takes 1,355–1,397 ms rather than
1,460–1,469, and Home appears at 1.85–1.86 s rather than 1.95–1.99.

Also new, and fixed: each artwork colour went into the store on its own,
and marking the next artwork as under way was another change, each with a
render of the page. Opening the Library with 50 colours to work out made 98
renders. Now the colours that arrive before a paint go in together, with
the next artworks marked in the same change: 19 renders, with 41–43 ms of
rendering for the colours rather than 139–146, and 5 ms in the colours'
watch rather than 54–60.

Done since, the first two stages of #99, as
[search-index-plan.md](search-index-plan.md) planned them: the page shows
before the search index is read, and the web worker decodes the index.
Reading the index took 1.15 s of the start's task, and Home's picks only
30 ms, so the index stays on the page. Home appears after 0.66–0.75 s
rather than 1.85–1.86 s, the longest task at the start takes 0.22–0.23 s
rather than about 1.45 s, and the picks come after 1.97–2.14 s.

The commits in podcast-clj, drop-in-search and like-minded aren't released,
so the app has them only with the `:local` alias, which now takes
drop-in-search from its checkout too.


## Changes you can see

The changes that alter what the page shows or how it moves, for you to
review. Each names its item and commit, and the Portfolio scene that shows
it, where there's one. The motion shows only where the device allows
motion, and with reduced motion each of them changes at once.

While something loads:

- **A line moves under the status of a slow load** (#35, d17234d). A
  status that's still there after a second, e.g. Loading the show…,
  Looking for episodes… or a directory's search, gets a line 2 px high and
  a third as wide as its text, a little below it, which moves to the right
  and back four times and then stops. With reduced motion there's no line.
  Scenes: the Episode page's while its feed loads, and Home while its picks
  are found.
- **A ring turns around Pause while the episode waits for its file** (#5,
  3a43571). After half a second of waiting, the player's Pause and the
  Episode page's button get a ring in the button's own colour, which turns
  with a gap in it, or stands still and dashed with reduced motion.
  Scenes: the player's and the Episode page's while it waits for data.
- **The page's frame shows at start** (#85, df2923c). The navigation and
  the page's heading show at once while the library is read, with three
  lines in the paper's soft rule below, each shorter than the one before,
  from a second on. Scene: while the app starts.
- **Skeleton rows hold a show's place while its feed loads** (#38,
  98ab7e8). Five rows of an artwork's square and two lines stand under
  Loading the show or Loading episodes, and three lines under Loading the
  episode, from a second on. Scene: the Episode page's while its feed loads.
- **The picks fill in after the page** (#45, 0e147df). Home's sections and
  More like this show their heading over Looking for episodes… for a moment
  before their picks. Scene: Home while its picks are found.
- **At the start, the picks wait for the search index** (#99). Home shows
  sooner, and its picks say Looking for episodes… until the web worker has
  decoded the index, about 1.3 s with the benchmark's 53 shows, so the line
  under that status moves too. An episode that ends in that time doesn't
  autoplay the next pick.
- **The small artwork shows first** (#23, 126e30b). A Show or Episode
  page's large artwork shows the small copy from the row or result you
  came from, scaled up, until the large one arrives.
- **A linked show opens with its card's title and artwork** (#24,
  4268684), rather than with the heading Show and no artwork, when you open
  it from a podroll or the publisher's other shows.

Subscribing and new episodes:

- **The bookmark drops in and pulses** (#58, 2dda6df). As you subscribe,
  the bookmark fills, drops a tenth of its height into place over 0.2 s,
  and then fades to half and back three times, 1.2 s each, while the
  subscription works. This is the bounce. Scene: the Show page's while it
  subscribes.
- **Unsubscribe empties the bookmark at once** (#34, 2d1adf6), and the
  page keeps its episodes rather than show Loading the show for a few
  seconds. Scene: the Show page's while it unsubscribes.
- **The button for new episodes takes the status line's place** (#62,
  1ca871a). On a show that you've opened before, new episodes wait behind
  a button where the line of the episodes' count was, so the list doesn't
  move down. Scene: new episodes that wait.

Motion where there was none:

- **Dialogs ease in** (#60, b40d090). A dialog, e.g. Clear the queue,
  fades in and rises half a rem over 0.2 s as its backdrop fades in, and
  goes at once.
- **Search's jump row scrolls smoothly** (#61, c2d0c4f) to a directory's
  list.
- **Small changes ease** (#66, a06510e). A folded section's caret turns
  over 0.2 s, a switch's knob slides, the volume panel fades in and fades
  out within its 0.3 s, and artwork brightens under the pointer over
  0.15 s.
- **The Library's artwork moves to its new place** (#78, f4cb654) over
  0.25 s as Show as or Sort by changes, while the rest cross-fades, and a
  change of Light or dark or of the contrast cross-fades the whole page.

Side effects of speed-ups that you may notice:

- The Library's tiles past the first dozen get their artwork as you scroll
  to them, so you may see their colour first (#40, fb6a66b).
- Back to a show more than five outside shows ago opens from the cache,
  which can show a short load (#94, 6eac94d).

Before the ranking, on 24 and 25 October, the branch had these already:

- The player's buttons lift on hover, and the volume panel lies over the
  seek bar (ade8a42).
- Every button lifts as a key, a light in its words' colour at 10%, and
  the outlines stay for More contrast (fbc3bd6).
- Subscribe is a bookmark before the show's title: a thin outline to
  subscribe, filled with a microphone cut out once subscribed, and it
  fills before the subscription indexes the feed (2bb4aa1, 13f2ac6).


## The ranking

| # | Item | Area | Hours | Gain | Score | Confidence | Status |
| ---: | --- | --- | ---: | ---: | ---: | --- | --- |
| 1 | [Check Home's empty library without counting every show's episodes](#1-check-homes-empty-library-without-counting-every-shows-episodes) | Rendering | 1 | 3 | 15 | medium | Done |
| 2 | [Send a refresh's found feeds to the page in Transit, not EDN](#2-send-a-refreshs-found-feeds-to-the-page-in-transit-not-edn) | Main thread | 2 | 3 | 12 | medium | Done |
| 3 | [Keep only the media keys the views read, so playback renders once a second](#3-keep-only-the-media-keys-the-views-read-so-playback-renders-once-a-second) | Rendering | 2.5 | 3 | 12 | high | Done |
| 4 | [Key a subscribed show's page on its own settings, not the whole profile](#4-key-a-subscribed-shows-page-on-its-own-settings-not-the-whole-profile) | Rendering | 3 | 3 | 12 | medium | Done |
| 5 | [Show a visible wait in the player while the episode buffers](#5-show-a-visible-wait-in-the-player-while-the-episode-buffers) | Perceived | 3 | 3 | 12 | medium | Done |
| 6 | [Restore the main content's scroll position on Back and Forward](#6-restore-the-main-contents-scroll-position-on-back-and-forward) | Perceived | 5 | 4 | 12 | medium | Done |
| 7 | [Build the search documents of a refresh's changed feeds in the worker](#7-build-the-search-documents-of-a-refreshs-changed-feeds-in-the-worker) | Main thread | 8 | 4 | 12 | medium | Done |
| 8 | [Listen on a release build, and run the end-to-end tests against it](#8-listen-on-a-release-build-and-run-the-end-to-end-tests-against-it) | Load | 8 | 4 | 12 | medium | Measured |
| 9 | [Skip rebuilding the main content when only the player's clock ticks](#9-skip-rebuilding-the-main-content-when-only-the-players-clock-ticks) | Rendering | 5 | 3 | 9 | medium | Done |
| 10 | [Paint the shell's frame and placeholders from index.html](#10-paint-the-shells-frame-and-placeholders-from-indexhtml) | Startup | 6 | 3 | 9 | low | Later |
| 11 | [Stop reading the index's ids and stored fields as EDN per document at open](#11-stop-reading-the-indexs-ids-and-stored-fields-as-edn-per-document-at-open) | Main thread | 7 | 3 | 9 | low | Done |
| 12 | [Batch renders into one per animation frame, rendering early only for DOM reads](#12-batch-renders-into-one-per-animation-frame-rendering-early-only-for-dom-reads) | Rendering | 8 | 3 | 9 | medium | Deferred |
| 13 | [Apply a refresh's changed feeds a few per task, yielding between them](#13-apply-a-refreshs-changed-feeds-a-few-per-task-yielding-between-them) | Main thread | 8 | 3 | 9 | medium | Done |
| 14 | [Encode a subscription's feed for the worker after the bookmark paints](#14-encode-a-subscriptions-feed-for-the-worker-after-the-bookmark-paints) | Perceived | 1.5 | 2 | 8 | medium | Done |
| 15 | [Let idle! wait for the next paint in Safari, which has no requestIdleCallback](#15-let-idle-wait-for-the-next-paint-in-safari-which-has-no-requestidlecallback) | Main thread | 1.5 | 2 | 8 | low | Done |
| 16 | [Reuse one Intl formatter per kind and language instead of one per value](#16-reuse-one-intl-formatter-per-kind-and-language-instead-of-one-per-value) | Rendering | 2 | 2 | 8 | medium | Done |
| 17 | [Keep the last few pages' views in ui.memo, not only the last](#17-keep-the-last-few-pages-views-in-uimemo-not-only-the-last) | Rendering | 2 | 2 | 8 | low | Done |
| 18 | [Cap a refresh's feeds in flight below the proxy's four slots](#18-cap-a-refreshs-feeds-in-flight-below-the-proxys-four-slots) | Network | 2.5 | 2 | 8 | medium | Deferred |
| 19 | [Let a subscription share the Show page's whole-feed fetch in flight](#19-let-a-subscription-share-the-show-pages-whole-feed-fetch-in-flight) | Network | 2.5 | 2 | 8 | medium | Done |
| 20 | [Offer Apple's artwork at 64–512 px in WebP in the srcset](#20-offer-apples-artwork-at-64512-px-in-webp-in-the-srcset) | Network | 2.5 | 2 | 8 | medium | Done |
| 21 | [Preload Charis Bold and cache the fonts under a versioned path](#21-preload-charis-bold-and-cache-the-fonts-under-a-versioned-path) | Load | 2.5 | 2 | 8 | medium | Done |
| 22 | [Encode and write the library after the next paint, not in the click's frame](#22-encode-and-write-the-library-after-the-next-paint-not-in-the-clicks-frame) | Main thread | 2.5 | 2 | 8 | low | Deferred |
| 23 | [Show the row's small artwork under a Show or Episode page's large one while it loads](#23-show-the-rows-small-artwork-under-a-show-or-episode-pages-large-one-while-it-loads) | Perceived | 2.5 | 2 | 8 | low | Done |
| 24 | [Open a linked show's page at once from the card that linked to it](#24-open-a-linked-shows-page-at-once-from-the-card-that-linked-to-it) | Perceived | 3 | 2 | 8 | high | Done |
| 25 | [Update the lock screen's buttons when the listener's track-buttons choice changes](#25-update-the-lock-screens-buttons-when-the-listeners-track-buttons-choice-changes) | Perceived | 3 | 2 | 8 | high | Done |
| 26 | [Memoize the subscription lookups that every Show and Episode render repeats](#26-memoize-the-subscription-lookups-that-every-show-and-episode-render-repeats) | Rendering | 3 | 2 | 8 | medium | Done |
| 27 | [Prefetch a show's feed start from its artwork and card links, and on focus](#27-prefetch-a-shows-feed-start-from-its-artwork-and-card-links-and-on-focus) | Network | 3 | 2 | 8 | medium | Done |
| 28 | [Keep drpodcast.nu's slugs.json and DR's feed checks between searches](#28-keep-drpodcastnus-slugsjson-and-drs-feed-checks-between-searches) | Network | 3 | 2 | 8 | medium | Done |
| 29 | [Give the lock screen the server's artwork thumbnails, not the original](#29-give-the-lock-screen-the-servers-artwork-thumbnails-not-the-original) | Network | 3 | 2 | 8 | medium | Done |
| 30 | [Cache what rows derive from URLs and ids: thumbnails, srcsets, hashes, slugs](#30-cache-what-rows-derive-from-urls-and-ids-thumbnails-srcsets-hashes-slugs) | Rendering | 3 | 2 | 8 | low | Done |
| 31 | [Build the Show page's episode rows lazily, only for the episodes it lists](#31-build-the-show-pages-episode-rows-lazily-only-for-the-episodes-it-lists) | Rendering | 3 | 2 | 8 | low | Done |
| 32 | [Fetch artwork 4 at a time and scale it 2 at a time](#32-fetch-artwork-4-at-a-time-and-scale-it-2-at-a-time) | Network | 3 | 2 | 8 | low | Done |
| 33 | [Time a subscription's indexing stages in podcast-clj's browser bench](#33-time-a-subscriptions-indexing-stages-in-podcast-cljs-browser-bench) | Measurement | 3.5 | 2 | 8 | high | Measured |
| 34 | [Unsubscribe after the paint, and keep the page's feed in view](#34-unsubscribe-after-the-paint-and-keep-the-pages-feed-in-view) | Perceived | 3.5 | 2 | 8 | medium | Done |
| 35 | [Draw a short, delayed busy line under the status of slow loads](#35-draw-a-short-delayed-busy-line-under-the-status-of-slow-loads) | Perceived | 3.5 | 2 | 8 | medium | Done |
| 36 | [List the cache database once for the artwork colours read at start](#36-list-the-cache-database-once-for-the-artwork-colours-read-at-start) | Startup | 3.5 | 2 | 8 | medium | Done |
| 37 | [Find an outside show's repeated runs in the worker for its picks](#37-find-an-outside-shows-repeated-runs-in-the-worker-for-its-picks) | Main thread | 4 | 2 | 8 | medium | Done |
| 38 | [Hold the Show and Episode pages' shape with delayed skeleton rows while a feed loads](#38-hold-the-show-and-episode-pages-shape-with-delayed-skeleton-rows-while-a-feed-loads) | Perceived | 4 | 2 | 8 | medium | Done |
| 39 | [Keep the refresh's indexing from holding up the listener's worker requests](#39-keep-the-refreshs-indexing-from-holding-up-the-listeners-worker-requests) | Main thread | 4 | 2 | 8 | medium | Stage 1 done |
| 40 | [Let the browser skip off-screen rows, and lazy-load the Library tiles past the first dozen](#40-let-the-browser-skip-off-screen-rows-and-lazy-load-the-library-tiles-past-the-first-dozen) | Rendering | 4 | 2 | 8 | low | Lazy tiles done |
| 41 | [Measure drop-in-search's merges, then cap them so one add never rewrites the whole index](#41-measure-drop-in-searchs-merges-then-cap-them-so-one-add-never-rewrites-the-whole-index) | Main thread | 6 | 2 | 6 | low | Done |
| 42 | [Read only new index segments when catching up with another tab](#42-read-only-new-index-segments-when-catching-up-with-another-tab) | Main thread | 7 | 2 | 6 | medium | Done |
| 43 | [Build a benchmark library of 50 shows for repeatable profiling](#43-build-a-benchmark-library-of-50-shows-for-repeatable-profiling) | Measurement | 7 | 2 | 6 | medium | Done |
| 44 | [While hidden, skip clock-only renders, render once per task, and catch up when shown](#44-while-hidden-skip-clock-only-renders-render-once-per-task-and-catch-up-when-shown) | Rendering | 7 | 2 | 6 | medium | Deferred |
| 45 | [Find the picks after the paint and keep them in the store](#45-find-the-picks-after-the-paint-and-keep-them-in-the-store) | Main thread | 8 | 2 | 6 | medium | Done |
| 46 | [Keep only the notes field that resolve/description shows, as the worker parses](#46-keep-only-the-notes-field-that-resolvedescription-shows-as-the-worker-parses) | Rendering | 8 | 2 | 6 | medium | Done |
| 47 | [Keep two of the proxy's four slots for the listener's own requests](#47-keep-two-of-the-proxys-four-slots-for-the-listeners-own-requests) | Network | 8 | 2 | 6 | low | Deferred |
| 48 | [Read an episode's MP3 tags without their artwork, after the element plays](#48-read-an-episodes-mp3-tags-without-their-artwork-after-the-element-plays) | Network | 11 | 3 | 6 | medium | Stage 1 done |
| 49 | [Move the library's play step and the no-op add out of a Play click's task](#49-move-the-librarys-play-step-and-the-no-op-add-out-of-a-play-clicks-task) | Main thread | 4.5 | 2 | 6 | low | Part done |
| 50 | [Preload the three @imported stylesheets so CSS loads in one round trip](#50-preload-the-three-imported-stylesheets-so-css-loads-in-one-round-trip) | Load | 0.75 | 1 | 5 | high | Done |
| 51 | [Show podcast:person pictures as the server's 64–128 px thumbnails](#51-show-podcastperson-pictures-as-the-servers-64128-px-thumbnails) | Network | 1 | 1 | 5 | high | Done |
| 52 | [Skip identical renders, guard the title, and read the theme colour after paint](#52-skip-identical-renders-guard-the-title-and-read-the-theme-colour-after-paint) | Rendering | 1 | 1 | 5 | medium | Done |
| 53 | [Preload /api/server from index.html so it arrives while the scripts evaluate](#53-preload-apiserver-from-indexhtml-so-it-arrives-while-the-scripts-evaluate) | Startup | 1 | 1 | 5 | low | Deferred |
| 54 | [Hash each served file once per version, not on every request](#54-hash-each-served-file-once-per-version-not-on-every-request) | Load | 1.5 | 1 | 4 | high | Done |
| 55 | [Tally timeupdates, store changes and renders per minute while hidden, in dev](#55-tally-timeupdates-store-changes-and-renders-per-minute-while-hidden-in-dev) | Measurement | 1.5 | 1 | 4 | medium | Later |
| 56 | [Start the web worker at idle after the first paint](#56-start-the-web-worker-at-idle-after-the-first-paint) | Startup | 1.5 | 1 | 4 | low | Done |
| 57 | [Run the e2e tests with reduced motion and check that all motion stays gated](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated) | Measurement | 2 | 1 | 4 | high | Done |
| 58 | [Pulse the bookmark while a subscription works, and stop within 5 s](#58-pulse-the-bookmark-while-a-subscription-works-and-stop-within-5-s) | Motion | 3 | 2 | 8 | medium | Done |
| 59 | [Glide queue, inbox and Library rows to their new places with a FLIP hook](#59-glide-queue-inbox-and-library-rows-to-their-new-places-with-a-flip-hook) | Motion | 7 | 2 | 6 | medium | Later |
| 60 | [Ease the Clear the queue dialog and its backdrop in, and leave its exit instant](#60-ease-the-clear-the-queue-dialog-and-its-backdrop-in-and-leave-its-exit-instant) | Motion | 1 | 1 | 5 | high | Done |
| 61 | [Scroll smoothly to a directory's list from Search's jump row](#61-scroll-smoothly-to-a-directorys-list-from-searchs-jump-row) | Motion | 1.5 | 1 | 4 | medium | Done |
| 62 | [Show 'Show the new episodes' in the status line's place, so the list stays put](#62-show-show-the-new-episodes-in-the-status-lines-place-so-the-list-stays-put) | Motion | 2 | 1 | 4 | medium | Done |
| 63 | [Start the first refresh after the first paint, at idle time](#63-start-the-first-refresh-after-the-first-paint-at-idle-time) | Startup | 2 | 1 | 4 | medium | Done |
| 64 | [Report what's in the release bundles, and fail CI over a size budget](#64-report-whats-in-the-release-bundles-and-fail-ci-over-a-size-budget) | Measurement | 2.5 | 1 | 4 | high | Done |
| 65 | [Measure proxy and thumbnail waits with Server-Timing before tuning them](#65-measure-proxy-and-thumbnail-waits-with-server-timing-before-tuning-them) | Measurement | 2.5 | 1 | 4 | high | Done |
| 66 | [Ease the caret, the switch's knob, the volume panel and the artwork hover](#66-ease-the-caret-the-switchs-knob-the-volume-panel-and-the-artwork-hover) | Motion | 2.5 | 1 | 4 | high | Done |
| 67 | [Cache the worker's encoded feed as it is, without encoding it again](#67-cache-the-workers-encoded-feed-as-it-is-without-encoding-it-again) | Main thread | 2.5 | 1 | 4 | medium | Done |
| 68 | [Write only the icons the code uses, if listening stays on the dev build](#68-write-only-the-icons-the-code-uses-if-listening-stays-on-the-dev-build) | Load | 2.5 | 1 | 4 | medium | Done |
| 69 | [Give the worker the library's options without the page's library namespace](#69-give-the-worker-the-librarys-options-without-the-pages-library-namespace) | Startup | 2.5 | 1 | 4 | medium | Done |
| 70 | [Cap the episodes a feed retains after its host drops them, with :max-items](#70-cap-the-episodes-a-feed-retains-after-its-host-drops-them-with-max-items) | Load | 2.5 | 1 | 4 | low | Declined |
| 71 | [Mark each stage from a Play click to the element's 'playing' event](#71-mark-each-stage-from-a-play-click-to-the-elements-playing-event) | Measurement | 3 | 1 | 4 | high | Done |
| 72 | [Fade in what arrives after the page: more episodes, results, opened sections](#72-fade-in-what-arrives-after-the-page-more-episodes-results-opened-sections) | Motion | 3 | 1 | 4 | medium | Declined |
| 73 | [Profile the 0.6 s seed transition, and narrow it if it drops frames](#73-profile-the-06-s-seed-transition-and-narrow-it-if-it-drops-frames) | Motion | 3 | 1 | 4 | low | Measured |
| 74 | [Keep copies of the palette, bar seed, player height and theme-color](#74-keep-copies-of-the-palette-bar-seed-player-height-and-theme-color) | Startup | 4 | 1 | 4 | medium | Later |
| 75 | [Fade each new page in, with main keyed by page and the announcer outside it](#75-fade-each-new-page-in-with-main-keyed-by-page-and-the-announcer-outside-it) | Motion | 4 | 1 | 4 | low | Declined |
| 76 | [Make list rows Replicant aliases so unchanged rows aren't expanded](#76-make-list-rows-replicant-aliases-so-unchanged-rows-arent-expanded) | Rendering | 10 | 2 | 4 | low | Deferred |
| 77 | [Show the last session's page from a saved snapshot until the app renders](#77-show-the-last-sessions-page-from-a-saved-snapshot-until-the-app-renders) | Perceived | 12 | 2 | 4 | low | Declined |
| 78 | [Morph the Library's tiles between list and grid, and cross-fade the theme](#78-morph-the-librarys-tiles-between-list-and-grid-and-cross-fade-the-theme) | Motion | 4.5 | 1 | 3 | medium | Done |
| 79 | [Time renders, library operations and worker codec work with User Timing](#79-time-renders-library-operations-and-worker-codec-work-with-user-timing) | Measurement | 5 | 1 | 3 | high | Done |
| 80 | [Build an open transcript's and chapter list's Hiccup once, not every render](#80-build-an-open-transcripts-and-chapter-lists-hiccup-once-not-every-render) | Rendering | 3 | 2 | 8 | low | Done |
| 81 | [Make the queue, inbox and shows cheap to recompute at each progress report](#81-make-the-queue-inbox-and-shows-cheap-to-recompute-at-each-progress-report) | Rendering | 6 | 2 | 6 | low | Part done |
| 82 | [Time each Nexus dispatch and count its renders in dev](#82-time-each-nexus-dispatch-and-count-its-renders-in-dev) | Measurement | 2 | 1 | 4 | high | Done |
| 83 | [Profile a release build, with the listener's library, not the dev build](#83-profile-a-release-build-with-the-listeners-library-not-the-dev-build) | Measurement | 3 | 1 | 4 | medium | Later |
| 84 | [Mark each startup stage and log the time to the first render](#84-mark-each-startup-stage-and-log-the-time-to-the-first-render) | Measurement | 3.5 | 1 | 4 | high | Done |
| 85 | [Render the page's frame at init, before the library is read](#85-render-the-pages-frame-at-init-before-the-library-is-read) | Startup | 4.5 | 2 | 6 | low | Done |
| 86 | [Decode each feed document as its fetch resolves in podcast-clj's read!](#86-decode-each-feed-document-as-its-fetch-resolves-in-podcast-cljs-read) | Startup | 3 | 1 | 4 | low | Deferred |
| 87 | [Log slow interactions and long frames with PerformanceObserver](#87-log-slow-interactions-and-long-frames-with-performanceobserver) | Measurement | 4 | 1 | 4 | medium | Done |
| 88 | [Add performance budgets to the end-to-end tests for the key flows](#88-add-performance-budgets-to-the-end-to-end-tests-for-the-key-flows) | Measurement | 9 | 2 | 4 | medium | Later |
| 89 | [Read the first page's feeds first and load the rest of the library after](#89-read-the-first-pages-feeds-first-and-load-the-rest-of-the-library-after) | Startup | 18 | 2 | 4 | low | Declined |
| 90 | [Measure long-list scrolling on phones in Chrome, Firefox and Safari](#90-measure-long-list-scrolling-on-phones-in-chrome-firefox-and-safari) | Measurement | 4.5 | 1 | 3 | high | Later |
| 91 | [Print main's grain on a page wrapper instead of a local background](#91-print-mains-grain-on-a-page-wrapper-instead-of-a-local-background) | Rendering | 5 | 3 | 9 | medium | Later |
| 92 | [Isolate main so each artwork's multiply stays in its scrolling layer](#92-isolate-main-so-each-artworks-multiply-stays-in-its-scrolling-layer) | Rendering | 4 | 2 | 8 | medium | Measured |
| 93 | [Measure the heap at start, after ten outside shows and after an hour of play](#93-measure-the-heap-at-start-after-ten-outside-shows-and-after-an-hour-of-play) | Measurement | 5 | 1 | 3 | high | Measured |
| 94 | [Keep only the last few outside shows' whole feeds in the store](#94-keep-only-the-last-few-outside-shows-whole-feeds-in-the-store) | Main thread | 5 | 1 | 3 | low | Done |
| 95 | [Measure each script's compile and evaluate time and coverage before splitting](#95-measure-each-scripts-compile-and-evaluate-time-and-coverage-before-splitting) | Measurement | 5.5 | 1 | 3 | high | Measured |
| 96 | [Build the libraries' load-time tables at first use](#96-build-the-libraries-load-time-tables-at-first-use) | Startup | 4 | 1 | 4 | low | Deferred |
| 97 | [Version the release's scripts by content and cache them as immutable](#97-version-the-releases-scripts-by-content-and-cache-them-as-immutable) | Load | 6 | 1 | 3 | low | Later |
| 98 | [Warm the next episode's redirect chain with a HEAD request before the end](#98-warm-the-next-episodes-redirect-chain-with-a-head-request-before-the-end) | Network | 6.5 | 1 | 3 | low |  |
| 99 | [Keep the search index in the worker and ask it for picks](#99-keep-the-search-index-in-the-worker-and-ask-it-for-picks) | Main thread | 50 | 3 | 3 | low | Part done |
| 100 | [Cache the release build's app shell in a service worker](#100-cache-the-release-builds-app-shell-in-a-service-worker) | Load | 14 | 1 | 2 | medium | Later |


## The first ten

Done in order, the first ten take about 44 hours. Four of them take the
repeated work out of playback: about four renders a second become one (#3),
Home stops counting every episode at each change of the profile (#1), a
subscribed show's page stops rebuilding at each 15 s report (#4), and the main
content stops rebuilding every second (#9). Two take the EDN read and the search
documents out of the refresh's long task (#2 and #7), which matters most at the
day's first refresh and after an OPML import. Two fix what a listener notices
without a profiler: a silent Pause while the episode buffers (#5), and losing
your place on Back (#6). Item #8 moves listening onto a release build, which
speeds every open and every CPU path and gives every later measurement an honest
baseline, so it's worth doing early although it ties at 12. Item #10 replaces
the blank paper at start with the app's own frame.

Some items with a high score sit far down because they wait for a measurement:
the grain (#91, gain 3) waits for the phone audit at #90, and the transcript and
list memos (#80 and #81) for the User Timing at #79. Build #79 sooner, and they
come up with it.


## The items

### 1. Check Home's empty library without counting every show's episodes

*Rendering · 1 h · gain 3 · score 15 · confidence medium*

**Problem.** Home only needs to know whether the library has a subscription, so
it can put About first (`elektrolyt/ui/home.cljc:63`). To find out, it builds
the Library page's list of shows, which counts every episode of every show
(`podcast/app/library.cljc:1103-1148`) and is memoized on the whole profile
(`elektrolyt/library/shows.cljc:32-35`). The profile changes at every 15 s
position report while playing (`elektrolyt/player.cljc:51-54`), at each queue
or inbox action, two to four times in one Play click
(`elektrolyt/player/audio.cljs:359-370`), and after a catch-up from another tab.
Replicant renders at each swap (`replicant/dom.cljs:201-234`), so each change
repeats an O(all episodes) pass on Home, and so does Home's first render. Not
measured on a large library.

**Fix.** In `ui/home.cljc`, test `(empty? (some-> library :profile
profile/subscriptions))`. That's the set that `profile/shows` orders
(`podcast/profile.cljc:115-118, 381-393`), so the answer is the same, in
O(subscriptions) and without settings or feeds. Avoid `library/subscriptions`,
which merges every subscription's settings for nothing. The Library page keeps
`all-shows` for its tiles. Add unit tests in
`test/dk/simongray/elektrolyt/ui/home_test.cljc`: About comes first with no
subscriptions, and after the lists with one, including one whose feed isn't
fetched yet. To prove Home no longer calls `shows/all-shows`, redefine it to
throw. A `with-redefs` of `library/shows` proves nothing, since `all-shows`
captured that function when it was defined.

**Effort and gain.** One expression, a require and two small tests. It takes a
pass over every episode out of Home's first render and out of every profile
change on Home: a hitch every 15 s while you listen there, and two to four in a
Play click from Up next or the picks. It grows with the library, and yours is
big.

**Risks.** None found. A subscription without a fetched feed still counts, as
now.

**Sources.** [web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[web.dev on INP](https://web.dev/articles/inp).


### 2. Send a refresh's found feeds to the page in Transit, not EDN

*Main thread · 2 h · gain 3 · score 12 · confidence medium*

**Problem.** When a refresh finds changed feeds (as the app starts, when feeds
are due while the page is visible, and right after an OPML import), the worker
answers `{:id id :found found}` as one EDN string
(`elektrolyt/worker/fetching.cljs:190-192`). The found map holds each changed
feed's whole document, already a Transit string of 0.1–2 MB
(`podcast/app/library.cljc:726-735`, `podcast/app/library/impl.cljc:1059-1062`).
The page reads it with cljs.reader one character at a time
(`fetching.cljs:52-57`; tools.reader 1.6.0's `cljs/tools/reader/edn.cljs:241-250`)
in the task that applies the refresh, and then `refreshed` decodes each
document again (`podcast/app/library.cljc:777-785`). That adds an estimated
40–100 ms per MB of changed feeds: tens to a few hundred ms at a day's first
refresh, and seconds after an import of 50–100 shows
(`elektrolyt/library/opml.cljs:158-168`).

**Fix.** In `answer!`, encode a refresh's answer as a fetch's answer already
is: `(fn [found] {:id id :encoded ((:encode (:codec app-library/options))
{:found found})})`. The client's listener (`fetching.cljs:229`) already
resolves an answer with `:encoded` with its decoded value, and
`elektrolyt/library/refresh.cljs:167` destructures it unchanged, so no new kind
of message is needed. Busy, progress and failure answers stay small EDN. Update
the docstrings of `answer!` and the namespace and STATE.md:99, and cite the
measurement at `fetching.cljs:42-45`. Add a test in fetching_test.cljs that a
found with Transit document strings, instants, sets and a report round-trips
through the client's dispatch.

**Effort and gain.** A one-line change on an existing path, a test, two
docstrings and a STATE.md row. The reindexing of each changed feed (about
225 ms for 209 episodes) stays and costs more, which is #7, hence 3. The cost of
reading a string literal as EDN is extrapolated from the measured read of a
feed's structure.

**Risks.** No new failures: a value that Transit can't write already fails
cljs.reader today. During an update, an older worker's plain EDN answer still
reaches the client's fallback branch.

**Sources.** [HTML's structured clone](https://html.spec.whatwg.org/multipage/structured-data.html#structuredserializeinternal),
[Surma, Is postMessage slow?](https://surma.dev/things/is-postmessage-slow/),
[the Transit format](https://github.com/cognitect/transit-format),
[tools.reader's EDN reader](https://github.com/clojure/tools.reader/blob/master/src/main/cljs/cljs/tools/reader/edn.cljs).


### 3. Keep only the media keys the views read, so playback renders once a second

*Rendering · 2.5 h · gain 3 · score 12 · confidence high*

**Problem.** While an episode plays, `[:player :media]` changes at every
timeupdate, about four times a second, though `whole-seconds`
(`elektrolyt/player.cljc:155-160`) exists so that the page renders once a
second. In play-nice, `media/state` puts the buffered, seekable and played ranges
in the map (`play_nice/media.cljs:106-122`), the end of the played range moves
with the position, and `whole-seconds` floors only the current time. So
`changed!` swaps the store and posts on the player's channel at every
timeupdate (`elektrolyt/player/audio.cljs:230-237`), each swap renders the
whole page and runs the colours watch (`elektrolyt.cljs:479-480`), and every
other open tab re-renders at each message (`audio.cljs:494-509`). Progress
events while the file downloads add more, even when paused. No view or effect
reads the ranges, the ready state or the network state (grep).

**Fix.** In player.cljc, add `(def shown-keys (conj told-keys :seeking
:error))`, and have `whole-seconds` do `(-> media (select-keys shown-keys)
(update :current-time floor))`. Update the namespace docstring's line on
`:media`, and cite HTML's played attribute and its 250 ms timeupdate rule in a
comment. Nothing else changes: `changed!`, the reporter and `buffering!` go
through `whole-seconds` and read only kept keys, and `tell!` still selects its
keys from the raw media. Extend `a-render-a-second`
(`test/dk/simongray/elektrolyt/player_test.cljc:57-59`) with maps that carry
played and buffered ranges and differ only within a second. Add an audio test
that two such states swap the store once, and a check in tabs.spec.mjs that a
second page gets about one message a second.

**Effort and gain.** A constant and one line, a docstring, a comment, two unit
tests and one assertion. It cuts playback's renders about fourfold (up to
sevenfold while downloading) in the visible tab, in hidden tabs and in every
other tab, in the app's most common state. It's from engine source and the
spec, but renders weren't counted in a browser, hence 3.

**Risks.** A future buffered band on the seek bar must round its ranges
coarsely, or the renders come back.

**Sources.** [HTML's played attribute](https://html.spec.whatwg.org/multipage/media.html#dom-media-played),
[HTML's time marches on](https://html.spec.whatwg.org/multipage/media.html#time-marches-on),
[Chromium's html_media_element.cc](https://raw.githubusercontent.com/chromium/chromium/main/third_party/blink/renderer/core/html/media/html_media_element.cc),
[Gecko's HTMLMediaElement.cpp](https://raw.githubusercontent.com/mozilla/gecko-dev/master/dom/html/HTMLMediaElement.cpp),
[WebKit bug 279055](https://bugs.webkit.org/show_bug.cgi?id=279055).


### 4. Key a subscribed show's page on its own settings, not the whole profile

*Rendering · 3 h · gain 3 · score 12 · confidence medium*

**Problem.** The `library-page` memo is keyed on the feed entry and the whole
profile (`elektrolyt/show.cljc:133-138`). So every 15 s report, every queue or
inbox change and each of the two to four library changes in a Play click
rebuild a subscribed show's whole page: `library/show` with its sort, an
`episode-row` per episode (`show.cljc:72-87`), the notes and the cadence. The
page reads only the show's settings and subscription, and the whole profile
only for the `:recent` order (`podcast/app/library.cljc:1067-1101`,
`podcast/criteria.cljc:143-162`). On Episode pages, `colors/page-show` builds
the same page only for its artwork (`elektrolyt/ui/colors.cljc:49-65`), so Play
on an episode of a long subscribed show rebuilds it at each change in the
click.

**Fix.** Key `library-page` on `[feed-entry (get-in profile [:settings k])
(get-in profile [:subscriptions k]) (when (:order (profile/settings profile k))
profile) k]`. Since `profile.player` writes neither settings nor subscriptions,
and `memo/same?` compares collections by identity
(`elektrolyt/ui/memo.cljc:5-10`), a step leaves the key as it was. Narrowing the
last part to the `:recent` order would couple the app to criteria's internals,
so keep the conservative key unless a measurement says otherwise. A smaller
second step gives `colors/page-show` a memoized `show/artwork-of`, which helps
a cold Episode page and shows with their own order. Tests in show_test.cljc: a
progress step and a queue change leave the page identical; a custom title, an
order or a feed change rebuild it; the result equals `(view->page
(library/show …))`; and `artwork-of` equals the page's artwork, including a
single 3000 px image and a search result's sizes.

**Effort and gain.** A key function and `artwork-of`, with their tests; views
and scenes don't change. It takes a rebuild of the whole show out of Play and
out of every 15 s step on Show and Episode pages. That's large for long
archives, whose build the docstring already calls slow. Not measured.

**Risks.** If podcast-clj's `library/show` starts reading another part of the
profile, the key goes stale; the equivalence test catches that.

**Sources.** [web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[web.dev on INP](https://web.dev/articles/inp),
[Replicant, top-down](https://replicant.fun/top-down/).


### 5. Show a visible wait in the player while the episode buffers

*Perceived · 3 h · gain 3 · score 12 · confidence medium*

**Problem.** After Play, the button turns into Pause at once, since the
element's paused flag flips as `play()` is called
(`elektrolyt/player.cljc:143-153`). While the file buffers (the first play,
after a seek, on a phone, behind a chain of analytics redirects), the clock
stands still and only a visually hidden status line says 'Loading…'
(`elektrolyt/player/ui.cljc:262-267`). Sighted listeners see Pause and hear
silence, and may press again and pause. The store already sets `:buffering?`
after 500 ms of buffering (`elektrolyt/player/audio.cljs:213-224`), but no CSS
reacts to it.

**Fix.** Pass `:buffering?` to `player-ui/play-button` as data-waiting, keeping
the name Pause and the status line, and set data-buffering on .player-bar. The
icon button merges extra attributes (`elektrolyt/ui/controls.cljc:124-129`).
Give the Episode page's play button the same attribute for that episode, and
draw its indicator in the icon's place, since it's a text button. In look.css's
no-preference block, draw a turning ring in the bar's tokens around
`.player-play[data-waiting]`, as Media Chrome's loading indicator does after the
same 500 ms. Under reduced motion draw a still dashed ring, and add a rule to
the forced-colors block. A faint sweep over the unplayed track through
`.player-bar[data-buffering]::after` is optional, placed in layout.css and
coloured in look.css. The buffering player already has a scene
(`dev/dk/simongray/elektrolyt/player/ui_scenes.cljs:60-63`); add one for the
Episode page, and an e2e test with the media route delayed 2 s that expects
data-waiting on both buttons.

**Effort and gain.** Two attributes, about 25 lines of CSS, a scene and an e2e
test. Starting and seeking are the most common actions, and stalls over 500 ms
are common on phones and behind redirect chains, though how often is
unmeasured. It also stops the press-again-and-pause mistake.

**Risks.** A long stall shows motion past 5 s, which WCAG 2.2.2 accepts for a
loading phase, and the reduced-motion ring doesn't move. The ring must keep 3:1
against every artwork's bar colour. It needs
[#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated)
only loosely: an animated pseudo-element doesn't move the button's box, so
Playwright's stability checks still pass.

**Sources.** [Media Chrome's loading indicator](https://www.media-chrome.org/docs/en/components/media-loading-indicator),
[NN/g on progress indicators](https://www.nngroup.com/articles/progress-indicators/),
[Understanding WCAG 2.2.2](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html),
[MDN on the waiting event](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/waiting_event).


### 6. Restore the main content's scroll position on Back and Forward

*Perceived · 5 h · gain 4 · score 12 · confidence medium*

**Problem.** Only `<main>` scrolls (`public/css/layout.css:235-263`), and the
same main#main stays from page to page (`elektrolyt/ui.cljc:221`). Browsers
restore inner scrollers during the traversal, before popstate, while main
still holds the page being left. The app then renders the old page and
focuses its h1 without preventScroll (`elektrolyt/ui/document.cljs:131-134`),
which scrolls main to the heading. So Back from an Episode page to a long Show
page, from a Show page to Search results, or to the Library or the Inbox,
lands at the top. Nothing in src or public saves a position (grep for
scrollRestoration, scrollTop and history.state).

**Fix.** Use the Navigation API (Chrome 102+, Firefox 147+, Safari 26.2+)
rather than keys in history.state. The reitit router pushes with nil state before it calls
on-navigate (reitit-frontend 0.11.0's `reitit/frontend/history.cljs:121-122`),
so history.state would already belong to the new entry.

- In a runtime namespace, keep a defonce atom of positions keyed by
  NavigationHistoryEntry key, and listen for navigation's navigate event. It
  fires for pushes, replaces and traversals before the commit, while
  currentEntry is still the page being left, so save main#main's scrollTop
  under that entry's key.
- In `start-router!`'s on-navigate (`elektrolyt.cljs:408-417`), pass the
  position saved for the new currentEntry with the route to `:route/navigated`.
- The `navigated` function stays pure. On a new page with a saved position, it
  emits `[:page/restore-scroll px]`, focuses the heading with preventScroll and
  skips `:page/scroll-to`. Without a saved position, or on a same-page
  navigation, nothing changes.
- A replace keeps its entry's key, so `canonicalize!` and `decodable!` need no
  change. Where js/navigation is undefined, nothing changes either.

Add STATE.md rows, unit tests of `navigated`, and an e2e test in Chrome and
Firefox that scrolls the Show page and Search results, follows a link, goes
Back, and expects main's scrollTop near the old value with the focus on the h1.

**Effort and gain.** About 30 lines in runtime, the root namespace and
ui.routes, with unit tests, an e2e test in two browsers and the STATE.md rows.
Focusing the followed link instead of the heading adds about an hour. The lost
place is certain on a loop that a listener repeats several times a session;
how far listeners scroll is unmeasured.

**Risks and principles.** Focus on the heading with preventScroll leaves the
focus above the view for keyboard and screen reader users. The followed link
is better, but needs its selector kept. Positions live in memory, so Back after
a reload goes to the top as now, and Safari before 26.2 gets no restore. The
atom and the listener go in a runtime namespace with STATE.md rows. If
[#75](#75-fade-each-new-page-in-with-main-keyed-by-page-and-the-announcer-outside-it)
keys main by page, restore after that render, on the new element.

**Sources.** [HTML on restoring persisted state](https://html.spec.whatwg.org/multipage/browsing-the-web.html#restore-persisted-user-state),
[the Navigation API's navigate event](https://github.com/WICG/navigation-api#the-navigate-event),
[MDN on the Navigation API](https://developer.mozilla.org/en-US/docs/Web/API/Navigation_API),
[InfoQ on its Baseline status](https://infoq.com/news/2026/05/navigation-api-browser),
[Chrome on history scroll restoration](https://developer.chrome.com/blog/history-api-scroll-restoration),
[Gatsby's user testing of client routing](https://v2.gatsbyjs.com/blog/2019-07-11-user-testing-accessible-client-routing).


### 7. Build the search documents of a refresh's changed feeds in the worker

*Main thread · 8 h · gain 4 · score 12 · confidence medium*

**Problem.** For each changed or new feed, `refreshed` rebuilds its search
documents on the page, all in one task: `noted` and `first-fetched` call
`settled`, then `reindexed` with no built documents, then
`feed.search/feed-documents` (`podcast/app/library/impl.cljc:186-203, 305-314,
558-623`; `podcast/feed/search.cljc:258-275`). That's about 171 of the 225 ms
per feed measured for Podcasting 2.0 in subscribe, which runs the same
functions. It also recomputes the runs that the worker found with
`repeated-runs` moments before (`podcast/app/library.cljc:726-735`). A day's
first refresh with several long changed feeds holds the page for a second or
more as the listener starts, and the first refresh after an OPML import of
50–100 shows holds it for tens of seconds. The subscribe fix moved this work to
the worker for subscriptions only.

**Fix.** In podcast-clj's `fetch-due!`, replace `repeated-runs` with
`feed-documents` over each changed subscription's fetched feed, keeping
`(:repeated (meta docs))` as the runs in the feed's document.

- Key the feed as the library will: the entry's key for an existing
  subscription, and the feed's own guid for one that `first-fetched` will rekey
  (`impl.cljc:518-542`, the rekey at 522). Otherwise the OPML case, the most
  expensive one, falls back to the page.
- Return the documents in found, e.g. `:search-documents {feed-guid {:url …
  :documents … :repeated …}}`, and pass them through `applied` to `noted` and
  `first-fetched`, then to `settled` as built documents. The `built-for` check
  already falls back to building on the page when the URL, the subscription or
  the ids differ (`impl.cljc:169-184`).
- Test on both platforms that `refreshed` with and without built documents
  gives the same index and runs, for a changed subscription, a first fetch that
  rekeys and a feed of remote items. Then run the app's refresh and OPML e2e
  tests, and release once with the subscribe work.

**Effort and gain.** Changes to `fetch-due!`, `refreshed`, `applied` and
`first-fetched` that reuse `built-for` and `settled`, tests on both platforms
and one release. It takes the largest measured per-feed cost out of the daily
refresh, and most of the tens of seconds out of an import's first refresh;
search/add, `without-feed` and the merges stay on the page. The cost is
measured in subscribe, not in the refresh path itself.

**Risks and principles.** The answer and the waiting refreshed operation grow by
the notes' text, so do #2 first. Operations replay on a conflict, and the
documents travel with the operation, so a replay can reuse them. The refresh's
result arrives later, since the worker does more before it answers. Documents
keyed differently from the library fall back to the page silently, so the
tests must cover the rekey. The change belongs in podcast-clj, as the subscribe
fix did.

**Depends on.** [#2](#2-send-a-refreshs-found-feeds-to-the-page-in-transit-not-edn).

**Sources.** [web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[web.dev on off-main-thread work](https://web.dev/articles/off-main-thread),
[Surma, When should you be using workers?](https://surma.dev/things/when-workers/),
[HTML's structured clone](https://html.spec.whatwg.org/multipage/structured-data.html#structuredserializeinternal).


### 8. Listen on a release build, and run the end-to-end tests against it

*Load · 8 h · gain 4 · score 12 · confidence medium*

**Problem.** Every page you load comes from the dev build, and no release of the
app has been made: `.shadow-cljs/builds/app` holds only dev/, and
`dev/user.clj:11-21` starts only the watches.

- Each load evals about 16.3 MB of JS from strings (shared.js 12.96 MB, main.js
  3.36 MB), which V8's code cache can't keep across loads. The worker evals
  13.1 MB more, since a dev web-worker module inlines the sources of the
  modules it depends on (`shadow/build/targets/browser.clj` in shadow-cljs
  3.5.7).
- On top come Replicant's per-node asserts (`replicant/env.clj:44-47`,
  `replicant/core.cljc:909-910`), goog.DEBUG, Telemere at its default level
  (`elektrolyt/runtime/log.cljs:32-33`), and shadow's devtools client with the
  core.async, cljs.pprint and spec.alpha it pulls in (`public/js/manifest.edn`).

So the blank at every open is longest here, every CPU path runs slower than a
listener's would, and every timing so far comes from this build. CI's e2e tests
run on dev too (`.github/workflows/test.yml:67`).

**Fix.**

1. Add `:release {:output-dir "target/release/js"}` to the `:app` build first.
   The shadow-cljs build merges the mode's section deeply over the rest
   (`shadow/build.clj:174-179`), so a release never overwrites the watch's
   public/js.
2. Measure: run `(shadow.cljs.devtools.api/release :app)` in your running JVM,
   never a fresh shadow JVM, and take two Performance traces (opening the app,
   and subscribing from a Show page) on dev and on release.
3. Serve the release on the same origin, since another port has an empty
   IndexedDB. A `listen!` function in dev/user.clj copies public/ without js/
   into target/release/ and restarts the server with `:client-dir
   "target/release"`, which `start!` already takes
   (`elektrolyt/server.clj:305, 313-315`), and `develop!` goes back to public.
   Add a `:release?` setting that drops 'unsafe-eval' from the CSP
   (`server.clj:279`).
4. Write a README paragraph on listening against developing.
5. In CI, build the release and run the e2e suite against it, with
   playwright.config.mjs taking the folder from an env var. Fix externs
   warnings with ^js hints.

**Effort and gain.** About 4 h for the config, the server setting with a JVM
test, `listen!` and `develop!`, the README and CI. :advanced has never run on
this app or its libraries, so allow 2–4 h for interop it breaks, some of it in
library checkouts that then need releases. The bytes are measured (about 16 MB
less eval per page load and 13 MB less in the worker), but the time saved
isn't, so it's a 4 until step 2 shows whether the blank and the freezes shrink
by seconds.

**Risks.** Untyped interop that breaks only under :advanced. A stray eval that
the CSP refuses (cljs.core's find-ns-obj and goog.json's legacy parse contain
one), which the release e2e run would catch. Worse stack traces without source
maps. Open tabs keep their build until reloaded, and the copy in
target/release/ lags CSS edits until `listen!` runs again.

**Sources.** [shadow-cljs on release configuration](https://shadow-cljs.github.io/docs/UsersGuide.html#_release_specific_vs_development_configuration),
[Replicant's build options](https://replicant.fun/build-options/),
[V8 on code caching](https://v8.dev/blog/code-caching-for-devs),
[V8, The cost of JavaScript in 2019](https://v8.dev/blog/cost-of-javascript-2019),
[Tonsky on ClojureScript](https://tonsky.me/blog/clojurescript-2/).


### 9. Skip rebuilding the main content when only the player's clock ticks

*Rendering · 5 h · gain 3 · score 9 · confidence medium*

**Problem.** While an episode plays here or in another tab, the store changes
every second (`elektrolyt/player/audio.cljs:226-237, 493-498`), and the whole
page body is rebuilt and diffed, since `page` calls `main-content` at every
render (`elektrolyt/ui.cljc:249-258`). Only the player, the shell's colour
attributes and the favicon read the clock. Main's views read the player only
through `player/playing` and `shown-id` (`elektrolyt/episode/ui.cljc:89`,
`elektrolyt/library/queue.cljc:30-35`). A Show page render took 12–14 ms in
dev, more on a phone and on a big Library, whose tiles are re-sorted at every
render. Scrolling runs on the compositor, so the cost shows as input delay on
taps and typing, and as battery.

**Fix.** In ui/memo.cljc, add an entry-wise identical? comparison of maps and a
`remember-last` that takes a comparator, since `same?` compares collections by
identity and a fresh dissoc never matches (`memo.cljc:5-10`). In player.cljc,
add `content-facts`: `[(:id player) (shown-id player) (:paused media) (boolean
(:error media)) (:id (elsewhere player))]`. In ui.cljc, memoize `main-content`
on `[(dissoc state :player) (player/content-facts player)]`. Replicant compares
the Hiccup with = before anything else, so an identical main costs almost
nothing (`replicant/core.cljc:719-720, 911-913`). Tests in ui_test.cljc: the
result is identical for states that differ only in the current time, `:now` or
`:seeking`, and new on a pause, an error or a new shown episode. Add a static
test, in the manner of runtime_test.clj, that main's view namespaces don't read
`shown-media`, `:current-time` or `:now`, and run the player e2e specs.

**Effort and gain.** A comparator, a memo variant, a facts function and the
wiring, with unit and static tests and player e2e runs. It removes the
per-second rebuild of the page body on every page during playback. That's
measured only as a whole Show page render in dev, with Replicant's dev asserts;
release, phones and the Library page aren't measured, and scrolling is
threaded, hence 3 rather than 4. With #3, the tick comes once a second.

**Risks.** A future main view that reads a player field outside
`content-facts` goes stale, which the static test guards against. The
`::colors` watch still rescans the lists at every tick
(`elektrolyt/ui/colors.cljc:94-105`).

**Sources.** [Replicant, top-down](https://replicant.fun/top-down/),
[Replicant on aliases](https://replicant.fun/alias/),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[web.dev on optimizing INP](https://web.dev/articles/optimize-inp).


### 10. Paint the shell's frame and placeholders from index.html

*Startup · 6 h · gain 3 · score 9 · confidence low*

**Problem.** Between navigation and the first render, the listener sees bare
paper. #app holds only a noscript (`public/index.html:18-20`) while the
deferred scripts load and evaluate, IndexedDB returns the whole library,
/api/server answers and the cached colours are read (`elektrolyt.cljs:475-482`).
Every cold start and reload shows it, longer on a phone and with a big
library. Its length is unmeasured.

**Fix.**

- Put the frame in #app between `<!-- shell -->` markers: a div.shell with
  data-palette=pages and data-shell=static, the skip link, the nav with its
  five links and inline SVG icons (each link with a data-refocus naming its
  live counterpart, e.g. `nav a[href="/queue"]`), and a main#main with
  aria-busy=true holding a visually hidden 'Loading…', the noscript and two or
  three aria-hidden placeholder blocks. The placeholders fade in after about
  1 s, with no shimmer and not under reduced motion.
- Factor `ui/frame` and `ui/skeleton` out of `ui/page`, and add a cljs test that
  renders the static variant with replicant.string and fails, printing the
  markup to paste, when index.html drifts.
- Add an external public/shell.js at the end of the body; the CSP allows
  script-src 'self' (`elektrolyt/server.clj:279`). It sets aria-current from
  the path. While the static frame is in the page, it catches clicks on its nav
  links with preventDefault and pushState, so that the router's first
  navigation opens the tapped page instead of a reload starting the wait again.
  It checks `closest('[data-shell=static]')`, which leaves Replicant's links to
  the router.
- Replicant clears #app at its first render and builds its own DOM in the same
  task (`replicant/dom.cljs:195-207`), so there's no flash.

Write an e2e test with /api/server delayed: the nav and main[aria-busy] show,
the nav doesn't shift when the app renders, and a nav tap during the wait lands
on that page without a second load. Make the fixtures wait for main without
aria-busy before clicking, add a Portfolio scene of `ui/skeleton`, and do a
VoiceOver and NVDA pass.

**Effort and gain.** The markup and two CSS rules are quick. Factoring the
frame, the drift test, shell.js, the delayed-server test, the fixtures (whose
`navLink` many specs use) and the screen reader pass take the rest. Every start
shows the app's frame at first paint instead of blank paper. It hides the wait
rather than shortening it, and the wait is unmeasured: plausibly a few hundred
ms on a desktop and about a second on a phone with a big library.

**Risks and principles.** The copy can drift from the views, which the test
guards. Tests that click nav links right after goto must wait. The bar colour,
the palette and the player stay default until the first render, which #74
addresses. It's in mild tension with views as pure functions of the state,
since a second copy of the frame lives in a static file. It isn't server
rendering, and Replicant renders everything once the JS runs.

**Sources.** [Chrome on the app shell](https://developer.chrome.com/blog/app-shell),
[NN/g on skeleton screens](https://www.nngroup.com/articles/skeleton-screens/),
[NN/g on response times](https://www.nngroup.com/articles/response-times-3-important-limits/),
[MDN on aria-busy](https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Attributes/aria-busy),
[HTML on defer](https://html.spec.whatwg.org/multipage/scripting.html#attr-script-defer),
[web.dev on CLS](https://web.dev/articles/cls).


### 11. Stop reading the index's ids and stored fields as EDN per document at open

*Main thread · 7 h · gain 3 · score 9 · confidence low*

**Problem.** At open, drop-in-search reads each document's id and stored fields
with its own `edn/read-string` (`drop_in_search/ciff.cljc:181-192, 516-526,
709-724`). For 15.6k episodes that's about 31k EDN reads of roughly 4–5 MB of
text, in the task that reads every feed document before the first render.
Every open reads the index, since `library/make` starts with an empty one and
`from-saved` reads it whenever the manifest differs
(`podcast/app/library.cljc:133, 2128-2147`). At the 1152 ms per 20 MB measured
for EDN in Chrome, that's an estimated 200–300 ms of blank screen in a large
library, repeated at catch-ups after the index changed and at save conflicts.
Unmeasured.

**Fix.** First measure `ciff/read-files` on podcast-clj's 15.6k-episode lab
library in Chrome, and split the time between the EDN reads and the postings.
If the reads are a large share, keep each document's stored value as its EDN
text in drop-in-search, from build time and from CIFF, and read it only for a
page of results (`drop_in_search.cljc:203`) and in `search/document`. That
needs no format change and removes most of the reads, though segment names
change once, since `content-name` hashes the text. Only if the ids still
dominate, write a segment's ids as one block in a codec that the caller gives
(functions, not a Transit dependency), with EDN as the default, and keep
collection_docid, reading of older files and a bumped segment version. Release
bottom-up, with tests on both platforms.

**Effort and gain.** About 1 h to measure and 6 h for lazy stored values, with
tests and releases of drop-in-search and podcast-clj; the ids block would add
about 6 h more. It shortens the task before the first render at every open in a
large library by an estimated 100–300 ms, and by much less in a small one.

**Risks and principles.** The first save after the upgrade rewrites every
segment. Lazy stored values must be read wherever drop-in-search hands them
out, and an ids block must stay in step with record order and deletes. The fix
belongs in the libraries.

**Sources.** [CIFF](https://github.com/osirrc/ciff),
[the Transit format](https://github.com/cognitect/transit-format),
[tools.reader's EDN reader](https://github.com/clojure/tools.reader/blob/master/src/main/cljs/cljs/tools/reader/edn.cljs).


### 12. Batch renders into one per animation frame, rendering early only for DOM reads

*Rendering · 8 h · gain 3 · score 9 · confidence medium*

**Problem.** Every swap renders the whole page synchronously
(`elektrolyt.cljs:479-480`, `elektrolyt/runtime.cljs:184-188`); Replicant
queues only a render that starts during another (`replicant/dom.cljs:195-236`).
So one action renders several times: a Play click about four or five times,
plus once when `play()` resolves (`elektrolyt/player/audio.cljs:305-320,
359-370`), Play next twice (`elektrolyt.cljs:295-302`), opening a cached show
twice, and a refresh once per progress message from the worker
(`elektrolyt/library/refresh.cljs:150`). A Show page render took 12–14 ms in
dev, and a playing page renders every second even when hidden. Nexus batches
effects of one kind within a dispatch, which doesn't cover several swaps inside
single effects.

**Fix.**

- In runtime.cljs, add `next-frame!` (one pending function per key, one rAF)
  and `flush-frames!`, with `soon!` where rAF doesn't exist.
- The `::render` watch writes the cheap head at once (title, favicon and
  data-theme, each guarded) and schedules the body with
  `(runtime/next-frame! ::render #(document/render! @store))`, skipping a state
  identical to the last one rendered. The `::colors` watch rides the same frame.
  Browsers pause rAF in a hidden page, so a background tab's head stays
  current while its body waits.
- The DOM effects `focus!`, `focus-heading!`, `show!`, `scroll-to!` and
  `open-dialog!` call `flush-frames!` first. The element function in
  player.audio flushes only when no audio element is in the page, or a Play
  click still renders three times through `report!`, `load!` and
  `lock-screen!`. The flush is synchronous, so `play()` stays inside the click's
  user activation, which iOS Safari needs, and `painted!` still works, since its
  rAF is queued after the render's.
- Add requestAnimationFrame to the pattern in
  `test/dk/simongray/elektrolyt/runtime_test.clj:14-22`, unit-test the scheduler
  with a stubbed rAF, update STATE.md's timers, and run every e2e spec in both
  browsers.

**Effort and gain.** A small scheduler, a split of head and body in
ui.document, flushes at about six sites and the runtime_test change. Most of the
time goes to the full e2e suite in two browsers and to checking focus moves. A
Play click drops from about five whole renders to one or two, a refresh renders
at most once a frame, and a hidden playing tab stops rebuilding its body; each
render costs the same as before.

**Risks.** A DOM read after a swap without a flush reads the old page. Today's
reads are all in the listed effects (grep), but a new one must remember to
flush. If this lands, the identical-state skip of #52 moves into it.

**Sources.** [HTML's event loop](https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model),
[MDN on requestAnimationFrame](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestAnimationFrame),
[HTML on user activation](https://html.spec.whatwg.org/multipage/interaction.html#tracking-user-activation),
[React 18's automatic batching](https://react.dev/blog/2022/03/29/react-v18#new-feature-automatic-batching),
[web.dev on optimizing INP](https://web.dev/articles/optimize-inp).


### 13. Apply a refresh's changed feeds a few per task, yielding between them

*Main thread · 8 h · gain 3 · score 9 · confidence medium*

**Problem.** The `refreshed!` effect applies everything a refresh found in one
`change!` (`elektrolyt/library/refresh.cljs:120-127`), and `applied` reduces
every feed in one call (`podcast/app/library/impl.cljc:558-623`). So the page
can't answer a click, key, scroll or player button until every changed feed is
reindexed. The freeze grows with the number of changed feeds times their
length, and the first refresh after an OPML import is the worst case. Even with
documents built in the worker (#7), each feed's `without-feed`, search/add
(54 ms for 209 episodes) and merges stay on the page.

**Fix.**

- Add a pure function to podcast-clj that splits a found into parts. The first
  holds the lists, report, counts, seconds and unchanged entries. Each later
  part holds one or a few changed feeds, sized by episode count, with their
  share of feeds, from, documents, search documents, changed and moved, plus
  `:at`, so that applying the parts in turn gives the same library as the
  whole. Splitting by feed is safe, since `refreshed` takes a feed only while
  its from matches (`podcast/app/library.cljc:788-794`).
- In refresh.cljs, apply each part as its own `change!`, so that a conflict
  replays it. Between parts, await a new `runtime/yielded!`: scheduler.yield()
  where it exists (Chrome 129+, Firefox 142+), and a MessageChannel or
  setTimeout promise in Safari, with a research comment.
- Merge the parts' results into one summary and one announcement, keep
  'Checking…' until the last part, and save once.
- Hold `mark-when-fetched!` while a refresh runs
  (`elektrolyt/library/opml.cljs:116-129`), so the OPML marks run once at the
  end.
- Test on both platforms that the parts give the same library and the same
  added, backfilled and moved as the whole found, and add an e2e test that a
  click during a refresh of several feeds is handled.

**Effort and gain.** A pure splitter with tests on both platforms and a release,
the yield helper with its fallback, the OPML watch, and an e2e test. Each long
task shrinks to one part, so an import's freeze of many seconds becomes
responsive progress. Without #7 a part can't be smaller than one feed, about
225 ms for a long one, so the daily refresh gets markedly smoother only with
both.

**Risks and principles.** Each part renders the page and runs the colours watch.
An action between parts acts on a half-applied refresh, and the picks can briefly
miss new episodes. Safari's fallback yield goes to the back of the task queue.
A part whose first fetch takes over a subscription in another part needs a
test. The splitter is a pure function of podcast-clj's format, so it goes
there; the yielding and the part size are app policy.

**Depends on.** [#2](#2-send-a-refreshs-found-feeds-to-the-page-in-transit-not-edn),
since the whole found is read as EDN in the message task before any part can
apply (`elektrolyt/worker/fetching.cljs:52-57`).

**Sources.** [web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[Chrome on scheduler.yield](https://developer.chrome.com/blog/use-scheduler-yield),
[MDN on Scheduler.yield](https://developer.mozilla.org/en-US/docs/Web/API/Scheduler/yield),
[MDN's compatibility data for Scheduler](https://github.com/mdn/browser-compat-data/blob/main/api/Scheduler.json).

### 14. Encode a subscription's feed for the worker after the bookmark paints

*Perceived · 1.5 h · gain 2 · score 8 · confidence medium*

**Problem.** Subscribing is meant to paint the filled bookmark before any heavy
work, but the documents path is chained on `whole-feed!`, not on `painted!`
(`elektrolyt/show/feeds.cljs:308-311`). When the Show page already has the
whole feed, `whole-feed!` returns a resolved promise (`feeds.cljs:285-286`), so
its `.then` runs in the click's microtask checkpoint, and `search-documents!`
Transit-encodes the whole parsed feed there before it posts it
(`elektrolyt/worker/fetching.cljs:288`). The frame with the bookmark waits for
that encode: an estimated tens of ms for a 2 MB feed and more for a large one.
Unmeasured.

**Fix.** Wait for the paint before the documents path without delaying a fetch:
chain `with-search-documents!` on `(js/Promise.all #js [(whole-feed! store url)
(runtime/painted!)])` and `change!` after it. When the page lacks the feed,
`whole-feed!` still starts its worker fetch at once; only the encode and the
post move after the frame. Correct the comment at `feeds.cljs:309`. Later, with
#39's lanes, the worker could keep the whole feeds it fetched last by URL and
time, so that a subscription sends only the URL.

**Effort and gain.** A few lines and a comment. The subscribe e2e tests cover
the behaviour, plus a look in the Performance panel that the bookmark's frame
comes before the encode. An occasional path, and the size is unmeasured.

**Risks.** The subscription completes a frame later, or up to 100 ms later in a
hidden page. Since you're reworking this path, fold it into that work.

**Sources.** [HTML's microtask checkpoint](https://html.spec.whatwg.org/multipage/webappapis.html#perform-a-microtask-checkpoint),
[HTML's event loop](https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks).


### 15. Let idle! wait for the next paint in Safari, which has no requestIdleCallback

*Main thread · 1.5 h · gain 2 · score 8 · confidence low*

**Problem.** The `runtime/idle!` helper falls back to setTimeout 0 without
requestIdleCallback (`elektrolyt/runtime.cljs:50-57`), which Safari and iOS
Safari have off by default (caniuse, through 27.2). There, work 'at idle' runs
right after the current task, usually before the next paint. Every cache write
goes through it (`elektrolyt/runtime/cache.cljs:80-84`), so `keep-show!`
encodes a whole feed for the cache right after the Show page renders its full
list (`elektrolyt/show/feeds.cljs:41-44, 114-119`), and in Safari the list
paints after the encode. Safari also lacks scheduler.postTask and
scheduler.yield.

**Fix.** Make the fallback `(.then (painted!) (fn [_] (f)))` instead of
`(soon! f)`, so the work runs once the frame with the change has painted;
`painted!` already stands a 100 ms timeout in for a hidden page
(`runtime.cljs:59-76`). Keep the requestIdleCallback branch. Update the
docstring, and cite caniuse and the event loop's rendering step. A Node test,
with neither rIC nor rAF, keeps today's behaviour through `painted!`.

**Effort and gain.** A one-line change, a docstring, a comment, a test and a
look at Safari's timeline. Safari only, but iPhones matter for a podcast app.
The size depends on encode times and is unmeasured.

**Risks.** In Safari, idle work runs about a frame later, so a cache write can
be lost if the page closes in that frame; the cache is best-effort. It's 'after
paint', not true idle, so heavy work can still meet the next input.

**Sources.** [caniuse on requestIdleCallback](https://caniuse.com/requestidlecallback),
[MDN on requestIdleCallback](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestIdleCallback),
[MDN's compatibility data for Scheduler](https://github.com/mdn/browser-compat-data/blob/main/api/Scheduler.json),
[HTML's event loop](https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model).


### 16. Reuse one Intl formatter per kind and language instead of one per value

*Rendering · 2 h · gain 2 · score 8 · confidence medium*

**Problem.** Every date, time, duration, list, language name and file size in
the views makes a new Intl formatter, or calls toLocale*String with options,
which builds one internally (`elektrolyt/ui/time.cljc:13, 31, 58-61`;
`elektrolyt/ui/text.cljc:41, 59, 69, 104-109`). The Library's tiles and the rows
of the Show, Inbox and Queue pages format per row at every render
(`elektrolyt/show/ui.cljc:126-127`, `elektrolyt/library/ui.cljc:55`,
`elektrolyt/library/inbox/ui.cljc:33`, `elektrolyt/library/queue/ui.cljc:31,
77`), and those pages render every playback second. A community benchmark in
Chrome 141 puts construction at about 33 µs against 1.5 µs for a reused
formatter's format call.

**Fix.** In ui/text.cljc, add a cljs-only cache of formatters keyed by a string
of kind, language and options, and call `.format` on the cached formatter in
`date-text`, `time-text`, `duration-text`, `listing`, `listing-parts`,
`language-name` and `quantity`, keeping the JVM branches. The Intl.Collator
kept in `elektrolyt/library/shows.cljc:39-43` is the pattern. Tests in
text_test.cljc and time_test.cljc check that the output doesn't change for
en-GB and da.

**Effort and gain.** About seven functions in two files, one small cache and
output tests. Tens of microseconds per value, so a few ms per render of long
lists.

**Risks.** A change of the page's language must give new formatters, which the
key takes care of.

**Sources.** [MDN on toLocaleDateString](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toLocaleDateString),
[MDN on Intl.DateTimeFormat](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DateTimeFormat),
[MDN on Intl.DurationFormat](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DurationFormat),
[the MeasureThat benchmark](https://measurethat.net/Benchmarks/Show/23674/0/new-intldatetimeformat-vs-new-datetolocaledatestring-vs),
[V8 on Intl](https://v8.dev/blog/intl).


### 17. Keep the last few pages' views in ui.memo, not only the last

*Rendering · 2 h · gain 2 · score 8 · confidence low*

**Problem.** The `remember-last` memo keeps one result
(`elektrolyt/ui/memo.cljc:12-29`). So Show A, then a linked show B, then Back
to A rebuilds A's whole page in the navigation's click, and the next visit to B
rebuilds B. The same happens between Episode pages, where `page-in` hashes the
feed's guids again. The three call sites are `show/library-page`,
`show/feed-page` and `episode/page-in` (`elektrolyt/show.cljc:133-138,
206-212`, `elektrolyt/episode.cljc:106-111`).

**Fix.** Add `remember-recent` to ui.memo with about four entries, the same
`same?` rule and oldest-first eviction, and use it at the three sites. The page
is asked for several times per render, always for the route's show, so four
entries don't thrash. The time function of `page-in` is a stable var
(`elektrolyt/episode/ui.cljc:43`), so its key can hit across visits. Add unit
tests and update the ui.memo row in STATE.md.

**Effort and gain.** A small pure function with tests, three call sites and a
STATE.md row. Going back and forth between shows or episodes becomes instant,
on an occasional path; nobody has timed a big show's `view->page`.

**Risks.** Each kept page holds a show's rows, and four is modest.

**Depends on.** [#4](#4-key-a-subscribed-shows-page-on-its-own-settings-not-the-whole-profile)
for `library-page`, whose key holds the whole profile until #4 narrows it; the
other two hit without it.

**Sources.** [web.dev on INP](https://web.dev/articles/inp).


### 18. Cap a refresh's feeds in flight below the proxy's four slots

*Network · 2.5 h · gain 2 · score 8 · confidence medium*

Two candidates proposed this option, and this entry merges them.

**Problem.** A refresh fetches feeds in groups of 8, podcast-clj's default
concurrency (`podcast/app/feeds.cljc:85-87, 367-371`), and each group waits for
its slowest feed (`podcast/app/feeds/impl.cljc:244-256`). A DR feed with
drpodcast.nu on fetches its sibling at the same time
(`elektrolyt/worker/fetching.cljs:112-135`), so 16 or more requests can be in
flight. They all go to the same-origin /proxy, which serves 4 at a time while
the rest hold their connections for up to 30 s
(`elektrolyt/server.clj:182-195, 260-261`), and the page and the worker share
the browser's six HTTP/1.1 connections per host. So in the seconds after the
app opens (`elektrolyt.cljs:467, 482`), a Show page's start, its whole feed, a
search, a prefetch or an uncached thumbnail can wait behind the refresh. The
app passes no concurrency (`elektrolyt/library/refresh.cljs:145-150`). The
mechanism is in the code, but the wait is unmeasured.

**Fix.** First check in DevTools, during a due refresh in your own library,
whether a page request shows Queueing or Stalled. Then pass `:concurrency` in
the refresh's options from `fetched!` in refresh.cljs: 3 without drpodcast.nu,
and 2 with alternative feeds on, since each DR feed costs two requests. The
worker's `found!` merges the options, and podcast-clj's `fetch-due!` passes the
concurrency on (`fetching.cljs:152-163`, `podcast/app/library.cljc:699-705`).
Make it a per-call option rather than a key of the library's config, since it's
this server's limit. Comment it with Chromium's six sockets per group and the
proxy's four. Add a unit test that the options carry it, and an e2e test of a
10-feed refresh that counts the routed /proxy requests in flight.

Two follow-ups go in podcast-clj. Replacing `in-time`'s groups with a sliding
pool that starts the next feed as each one ends wins back the throughput that
groups lose (about 2 h). Building each changed feed's runs as its fetch ends,
instead of in one block at the end (`podcast/app/library.cljc:725-734`),
overlaps with
[#39](#39-keep-the-refreshs-indexing-from-holding-up-the-listeners-worker-requests).

**Effort and gain.** One option with a cited comment, the DR case, a unit test,
an e2e test and a look in DevTools; the pool is extra. The listener's own
requests stop waiting behind the worker, and the proxy hands out fewer 503s.

**Risks.** With groups that wait for their slowest feed, a cap of 3 makes a
refresh roughly a third slower, more when one feed is slow, so new episodes
reach the Inbox later; the pool removes that. The config is shared by every tab
and the worker.

**Sources.** [Chromium's client_socket_pool_manager.cc](https://chromium.googlesource.com/chromium/src/+/HEAD/net/socket/client_socket_pool_manager.cc),
[MDN on HTTP/1.x connections](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Connection_management_in_HTTP_1.x),
[Chrome DevTools on timing](https://developer.chrome.com/docs/devtools/network/reference#timing).


### 19. Let a subscription share the Show page's whole-feed fetch in flight

*Network · 2.5 h · gain 2 · score 8 · confidence medium*

**Problem.** Pressing Subscribe before a large show's whole feed has arrived
starts a second worker fetch of the same URL. While the Show page's fetch is in
flight, the feed isn't marked whole (`elektrolyt/show.cljc:243-249, 352-360`),
so `whole-feed!` calls `fetching/fetch!` again with the same options
(`elektrolyt/show/feeds.cljs:132-143, 281-288`), and `ask!` posts a new request
(`elektrolyt/worker/fetching.cljs:256-279`). The feed may download twice, and
it's always parsed twice in the worker and decoded twice on the page, so the
subscription waits behind both parses.

**Fix.** In worker.fetching, keep each `fetch!`'s promise in a defonce atom keyed
by `[url opts]`, removed when it settles, and hand an equal request the promise
in flight; `whole-opts` gives both callers the same options. An alternative
that adds no place: have `subscribe!` wait on a store watch until `[:whole url]`
is answered, then read `show/whole-feed`. Add a STATE.md row for the atom, a
unit test that two calls post once and that a rejection empties the map, and an
e2e test that subscribing while the whole feed is held back makes one request.
Build it on the current `subscribe!`.

**Effort and gain.** A small map with a STATE.md row and two tests. Up to a
second for a 20 MB feed and nothing for a small one, on an occasional path.

**Risks.** A rejected promise must leave the map, so that a retry fetches
again. The check of a cached show sends validators and other options, so it
stays separate, as it should.

**Sources.** [SWR on deduplication](https://swr.vercel.app/docs/advanced/performance),
[Fetch on HTTP cache partitions](https://fetch.spec.whatwg.org/#http-cache-partitions).


### 20. Offer Apple's artwork at 64–512 px in WebP in the srcset

*Network · 2.5 h · gain 2 · score 8 · confidence medium*

**Problem.** Apple's results name artwork at only 100 and 600 px
(`podcast/directory/impl.cljc:230-248`). So a 64 px result at 2x or 3x loads the
600 px JPEG (`elektrolyt/ui/artwork.cljc:41-47, 60-63, 77-91`), and the Show
page's intro loads it too, since the page keeps Apple's sizes when the feed
names one (`elektrolyt/show.cljc:197-204`). Measured with curl on a live
artwork: the 600 px JPEG is 35,481 B, while Apple's CDN serves 64x64bb.webp at
4,088 B, 128 px at 5,046 B, 256 px at 7,140 B and 512 px at 11,202 B.

**Fix.** In podcast-clj's `apple-show`, when artworkUrl600 ends in
/600x600bb.jpg, add images of 64, 128, 256 and 512 px as {w}x{w}bb.webp beside
Apple's two. The app needs no change. Say in a comment that this is how Apple's
image CDN behaves rather than a documented API, citing the posts that list the
sizes and the measurement. Add directory tests with a fixture, look at the
scenes in Portfolio, and release it with podcast-clj's other changes.

**Effort and gain.** A pure function with tests and a fixture. Each Apple
result's artwork gets about 7x smaller at 2x, and the intro's about 3x, which
shows on phones and slow links, on an occasional path.

**Risks.** If Apple stops serving the pattern, those candidates fail, and an img
doesn't fall back to its src when a srcset candidate fails. Cached search lists
keep the old sizes until they expire. Apple results' colours then come from the
64 px WebP through the proxy, which createImageBitmap decodes in all three
browsers.

**Sources.** [Extracting album covers from the iTunes Store](https://feeding.cloud.geek.nz/posts/extracting-album-covers-from-the-itunes-store/),
[Ben Dodson's iTunes artwork finder](https://bendodson.com/weblog/2013/02/19/itunes-artwork-finder),
[web.dev on WebP](https://web.dev/articles/serve-images-webp),
[HTML on selecting an image source](https://html.spec.whatwg.org/multipage/images.html#selecting-an-image-source).


### 21. Preload Charis Bold and cache the fonts under a versioned path

*Load · 2.5 h · gain 2 · score 8 · confidence medium*

**Problem.** Only Charis Regular is preloaded (`public/index.html:12`), yet every
button, every h2–h4 and the player's title are bold
(`public/css/look.css:30-41, 338-342, 812-815`). Since #app is empty until the
JS runs, the browser asks for Charis-Bold.woff2 (317 kB) only once the first
render puts bold text on the page. With font-display: swap
(`public/css/tokens.css:13-43`), that text paints in the fallback and then
swaps, which moves headings and buttons; the `scroll-to!` effect already
scrolls a second time on fonts.ready because of it
(`elektrolyt/ui/document.cljs:151-167`). Every font is also sent with no-cache
(`elektrolyt/server.clj:123-133`), so each load revalidates it. On localhost
both cost a frame or two, and on your Mac Charter stands in and differs little,
so the reflow shows mostly on other systems and once the server is remote.

**Fix.**

1. Add `<link rel="preload" href="/fonts/charis-<version>/Charis-Bold.woff2"
   as="font" type="font/woff2" crossorigin>` after Regular, with a comment that
   names the headings and buttons. The italics stay on demand.
2. Move the fonts to a folder named for SIL's version, e.g.
   public/fonts/charis-7.000/, update the four src URLs in tokens.css and both
   preloads, and have the revalidating interceptor send `Cache-Control: public,
   max-age=31536000, immutable` for /fonts/ alone. The grain image keeps
   no-cache, since it's the app's own file and can change under its name.
3. Add a JVM test of the headers, and an e2e test that the Bold URL's resource
   timing reports initiatorType "link". Don't check document.fonts right after
   the first render: a preloaded file still loads its FontFace asynchronously,
   so that check races.

A fallback face matched to Charis's metrics is a separate follow-up of about
3 h, which helps only off Apple devices.

**Effort and gain.** A preload line, four moved files, a path rule with its JVM
test and one e2e test. It removes a visible reflow at the first render, mostly
off Apple devices and once remote. Unmeasured.

**Risks.** Once remote, a font preload gets a high priority and goes ahead of
the deferred scripts. With immutable caching that's a first-visit cost, and
fetchpriority="low" can be weighed then. A font change that forgets the new
folder serves stale files for a year.

**Sources.** [web.dev on fonts](https://web.dev/articles/font-best-practices),
[web.dev on preloading](https://web.dev/articles/preload-critical-assets),
[RFC 8246](https://www.rfc-editor.org/rfc/rfc8246),
[MDN on fetchPriority](https://developer.mozilla.org/en-US/docs/Web/API/HTMLLinkElement/fetchPriority),
[MDN on ascent-override](https://developer.mozilla.org/en-US/docs/Web/CSS/@font-face/ascent-override),
[Resource Timing's initiatorType](https://www.w3.org/TR/resource-timing/#dom-performanceresourcetiming-initiatortype),
[the OFL FAQ](https://openfontlicense.org/ofl-faq/).


### 22. Encode and write the library after the next paint, not in the click's frame

*Main thread · 2.5 h · gain 2 · score 8 · confidence low*

**Problem.** Every library change calls `save!` (`elektrolyt.cljs:41-47`), and
the player saves at each 15 s report (`elektrolyt/player/audio.cljs:81-88`).
The save starts in a microtask. Once IndexedDB lists its keys, a task encodes
the library's own document in Transit, names every document, prints the index
manifest and CIFF-encodes any new segment (`podcast/app/library.cljc:2040-2067,
2281-2284, 2300-2305`). If IndexedDB answers before the next frame, that task
runs before the click's frame paints and delays it. The cost grows with the
profile, and neither its length nor its timing against the frame is measured.

**Fix.** Measure the save task in your library with the Performance panel
first. Then have `save!` wait for `runtime/painted!` inside its turn, before
`written!` (`elektrolyt/library.cljs:100-106, 113-142, 175-185`). Add a
`save-now!` without the wait for the hidden handler
(`elektrolyt.cljs:465-466`), Try again and Clear cache, since `painted!` waits
up to 100 ms in a hidden page and a closing tab may not get that long. Don't
use scheduler.postTask or requestIdleCallback, which Safari lacks. Cite the
event loop's processing model and the Page Lifecycle's rules, add a unit test
that a hidden page saves at once, and an e2e test that a queued episode
survives a reload.

**Effort and gain.** A small change that reuses `painted!`, a `save-now!` for
three callers and two tests. It keeps a click's frame from waiting for its save
when IndexedDB answers first, which is worth little if the document encodes in
a few ms.

**Risks.** A tab killed within the extra frame loses that change; the warning
before unload covers only failed saves.

**Sources.** [HTML's event loop](https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[Chrome on the Page Lifecycle](https://developer.chrome.com/docs/web-platform/page-lifecycle-api),
[MDN's compatibility data for Scheduler](https://github.com/mdn/browser-compat-data/blob/main/api/Scheduler.json),
[MDN on requestIdleCallback](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestIdleCallback).


### 23. Show the row's small artwork under a Show or Episode page's large one while it loads

*Perceived · 2.5 h · gain 2 · score 8 · confidence low*

**Problem.** From a search result, an episode row or a card, the page's intro
needs a larger thumbnail than the one just shown: it asks for 512w at 2x
(`elektrolyt/ui/artwork.cljc:16-20, 68-71`; `elektrolyt/show/ui.cljc:251-252`,
`elektrolyt/episode/ui.cljc:154-155`). Until it arrives the box shows the
dominant colour (`elektrolyt/ui/colors.cljc:128-132`) or nothing, perhaps while
the server scales a 3000 px original, though a smaller size sits in the HTTP
cache: thumbnails carry a week's max-age
(`elektrolyt/server/artwork.clj:52, 299-303`).

**Fix.** Give `artwork/img` an `:under` option, merge its style with the
placeholder's instead of replacing it, and set background-image to
`image-set(url(64w) 1x, url(128w) 2x)` with background-size: cover beside the
colour. Those are the sizes that a 64 px search result and a 48 px row load at
each density. The intros in show/ui and episode/ui pass it; the Library grid at
160 px already loads the intro's own width. Add a unit test of the attributes,
Portfolio scenes and a look in three browsers with the thumbnails throttled.

**Effort and gain.** A few lines, a style merge, two call sites and the checks.
Polish on a common path: the colour already fills the box when it's known, and
the wait for the large size is unmeasured.

**Risks.** Transparent artwork shows the soft copy through. On a deep link the
guessed size may cost one extra request at low priority.

**Sources.** [CSS Wizardry on LQIP](https://csswizardry.com/2023/09/the-ultimate-lqip-lcp-technique/),
[manu.ninja on dominant colours](https://manu.ninja/dominant-colors-for-lazy-loading-images/),
[MDN on image-set()](https://developer.mozilla.org/en-US/docs/Web/CSS/image/image-set).


### 24. Open a linked show's page at once from the card that linked to it

*Perceived · 3 h · gain 2 · score 8 · confidence high*

**Problem.** A show opened from a podroll or 'More from its publisher' card is
usually outside the library. Until its cache entry or feed start arrives, its
page shows the h1 'Show' and 'Loading the show…' in the app's own colours
(`elektrolyt/show/ui.cljc:323-339`, `elektrolyt/ui.cljc:66-75`), though the
card had its title, artwork and feed URL (`elektrolyt/show/linked.cljc:18-32`).
The page falls back only to a search result (`elektrolyt/show.cljc:190-195,
214-223`). Cards also lack the hover prefetch and the load on pointerdown that
search results have (`elektrolyt/search/ui.cljc:108-116`).

**Fix.** In show.cljc, add `known-result`: the search result, or else a card
with the route's guid among the podroll and publisher shows of any entry in
`:linked`. Use it in `page` instead of `search-result`, so that the h1, the
artwork, the colours, the bookmark and 'Loading episodes…' show in the first
frame, and the page's title, language and canonical path follow. A card has the
keys that `result->page` reads (`show.cljc:95-103`). Give the cards' title links
the mouseenter, mouseleave and pointerdown actions of `search-ui/show-link`,
moved to a shared place (#27 does the same for artwork links). Add a show_test
for a page from a card, a Portfolio scene, an e2e test that opens a podroll card
with the feed's route held and expects the card's title as h1, and a screen
reader check that the status line still announces.

**Effort and gain.** About ten lines that reuse `result->page`, a few for the
shared handlers, and the tests. The page goes from blank to nearly complete in
the first frame, but only on the podroll and publisher path, which few feeds
offer.

**Risks.** A podroll's title can differ from the feed's, so the h1 may change
when the feed arrives. After Clear cache or on a deep link, `:linked` is empty
and the page behaves as now.

**Sources.** [NN/g on response times](https://www.nngroup.com/articles/response-times-3-important-limits/),
[Remix on pending UI](https://v2.remix.run/docs/discussion/pending-ui/),
[instant.page](https://instant.page/),
[Inclusive Components on cards](https://inclusive-components.design/cards/).


### 25. Update the lock screen's buttons when the listener's track-buttons choice changes

*Perceived · 3 h · gain 2 · score 8 · confidence high*

**Problem.** The lock screen's Next and Previous are set only when an episode
loads, when the player mounts and when the episode's files are read
(`elektrolyt/player/audio.cljs:144-155`, called at 291, 319 and 587). The
default choice is 'Chapters' (`podcast/profile.cljc:449`), so an episode
without chapters gets neither (`podcast/app/media_session.cljc:48-59`). A
listener who then picks 'The episodes of the queue' in Settings, in this tab or
another, still has no Next until the next episode loads or the page reloads
(`elektrolyt.cljs:344-350`), so the setting looks broken. Switching back leaves
buttons that do nothing.

**Fix.** Add a `::lock-screen` watch beside `::render` and `::colors` that calls
`audio/lock-screen!` when a cheap key changes: `[(:id player) (:track-buttons
(profile/preferences (:profile library))) (get-in episodes [id :result])
(get-in library [:feeds (first id)])]`. The last part is the playing episode's
own feed entry, so a refresh of other shows doesn't set the handlers again.
Compare with identical? against the last key, kept in the watch's closure, and
skip computing the preferences when the profile is identical to the last. That
covers changes from another tab and a refresh that changes the episode's title
or artwork, so the explicit call after the files are read
(`audio.cljs:290-291`) can go. Add a unit test in audio_test with
`runtime/lock-screen!` redefined, and an e2e test in settings.spec.mjs whose
init script records the setActionHandler calls.

**Effort and gain.** A small watch, one call removed, two tests and a cited
comment. An occasional path, but the lock screen visibly contradicts the choice
for the rest of the episode.

**Risks.** Setting the handlers again can make some platforms redraw their
media notification, and play-nice's `actions!` doesn't skip unchanged ones
(`play_nice/session.cljs:246-269`), so the watch must call only when the key
changes.

**Sources.** [the Media Session spec](https://w3c.github.io/mediasession/#dom-mediasession-setactionhandler),
[MDN on setActionHandler](https://developer.mozilla.org/en-US/docs/Web/API/MediaSession/setActionHandler).


### 26. Memoize the subscription lookups that every Show and Episode render repeats

*Rendering · 3 h · gain 2 · score 8 · confidence medium*

**Problem.** Each render of a Show or Episode page calls `show/source` about 12
times: through the page, its status, the header, the subscribe button, the
picks' key functions, the page's title and language, and `colors/page-show` in
both the attributes and the `::colors` watch. Each call builds a new map of the
live subscriptions (`elektrolyt/show.cljc:105-109`) and runs `profile/by-url`,
which sorts every subscription by a vector key and normalizes each URL with
several regexes (`podcast/profile.cljc:120-133`, `podcast/feed/guid.cljc:28-46`).
The `search/subscribed-urls` function normalizes every subscription again, two
or three times per render (`elektrolyt/search.cljc:191-198`), and `show/search-result`
builds a vector per past search (`show.cljc:190-195`). That's negligible in a
near-empty library, but with 100–300 subscriptions it's likely a few ms per
render, more on a phone, at every click and playback second. Not measured.

**Fix.** In show.cljc, memoize `profile/by-url` and `profile/subscriptions` with
`memo/remember-last` on `[(:subscriptions profile)]`, the only part of the
profile they read. Since `profile.player` never writes it, the key survives
the 15 s steps. Use the memoized versions in `subscribed-key` and
`subscription-of`. Memoize `search/subscribed-urls` on the same key and
`show/search-result` on `[(:searches state) guid]`. Tests in show_test.cljc and
search_test.cljc: each equals the unmemoized function, returns an identical
result on a repeat call and after a progress step, and changes after a
subscription and an unsubscription. An index by URL can move to podcast-clj later.

**Effort and gain.** Four memo wrappers with equivalence and invalidation tests.
Polish that grows with the subscriptions, while Replicant's own diff stays.

**Risks.** A stale result shows the wrong subscription state, so a test pins
that the key is all these functions read.

**Sources.** [Replicant, top-down](https://replicant.fun/top-down/),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks).


### 27. Prefetch a show's feed start from its artwork and card links, and on focus

*Network · 3 h · gain 2 · score 8 · confidence medium*

**Problem.** The 65 ms hover prefetch and the load on pointerdown sit only on a
search result's title link (`elektrolyt/search/ui.cljc:108-116`;
`elektrolyt/show/feeds.cljs:270-274`). Hovering over or pressing a result's
artwork (`search/ui.cljc:135-136`, `elektrolyt/ui/artwork.cljc:98-103`),
tabbing to a result, or pointing at a podroll or publisher card
(`elektrolyt/show/ui.cljc:261-278`) fetches nothing until the click. The Show
page then opens with no episodes until the feed's start comes back through the
proxy.

**Fix.** Write one function that returns a show's pointer handlers as data:
`{:mouseenter [[:show/prefetch-soon guid url]] :mouseleave
[[:show/prefetch-cancel]] :pointerdown [[:show/load guid url true]]}`. Give
`artwork/linked` an attrs argument that it merges into its anchor, and use the
function on a result's artwork and title links and on the cards' artwork and
title links. Give only the focusable title links focus and blur handlers, with
a longer delay (a delay argument to `:show/prefetch-soon`), since a keyboard
user tabbing through 20 results stops briefly at each. Leave out colour and
image preloads, which the server's thumbnail job makes redundant
(`elektrolyt/server/artwork.clj:227-245`), and saveData, which only Chromium
has. Add unit tests of the handler map, and e2e tests that a hover on a result's
artwork brings the episodes sooner and that Tab to a title makes a head
request.

**Effort and gain.** A shared function, an attrs argument, three call sites, a
focus delay and the tests. Polish on discovery, unmeasured.

**Risks.** More head requests through the proxy's four slots, one per hover or
focus past the delay, and on phones a pointerdown at the start of a scroll
fetches a head too. The `load-show!` effect never fetches a show twice
(`feeds.cljs:250-268`). The artwork link stays aria-hidden and out of the tab
order.

**Sources.** [instant.page](https://instant.page/),
[MDN on saveData](https://developer.mozilla.org/en-US/docs/Web/API/NetworkInformation/saveData),
[Inclusive Components on cards](https://inclusive-components.design/cards/),
[WCAG technique H2](https://www.w3.org/WAI/WCAG22/Techniques/html/H2).


### 28. Keep drpodcast.nu's slugs.json and DR's feed checks between searches

*Network · 3 h · gain 2 · score 8 · confidence medium*

**Problem.** The drpodcast.nu source is on by default
(`elektrolyt/library/settings.cljc:57`). Every new query (lists are cached per
query for a day, `elektrolyt/search/directories.cljs:75-92`) fetches
slugs.json (117 kB) with no-store (`directories.cljs:39`,
`elektrolyt/runtime/cache.cljs:54-60`) and asks DR's API up to 20 times at once
(`directories.cljs:18-32, 43-47`). All of it goes through the proxy's four
slots, just as the listener hovers over and opens results. DR's answers carry a
max-age of 300–705 s and ignore the range, so the HTTP cache keeps them only
minutes.

**Fix.**

1. Fetch slugs.json with `app-library/send` in the default cache mode, as the
   worker does (`elektrolyt/worker/fetching.cljs:64-79`), so the page and the
   worker share one HTTP cache entry that revalidates by ETag. Say in a comment
   that the URL holds no query, so the reason for no-store doesn't apply.
2. Add a like-minded cache, e.g. 'dr-feed.' with 2000 entries, of each slug's
   answer, published or not. Read it first in `unpublished!`, and cache only
   real answers (a 404 or a 2xx), never a failure, which counts as unpublished
   today. Keep them about a day, since an unpublished series can be published,
   and clear the cache with the others.

Add a STATE.md row, and send the checks through #47's lane once it exists. Add a
unit test with a counting send, and an e2e test that counts requests across two
searches that share series.

**Effort and gain.** A one-line change of send, a cache config and a STATE.md
row, a failure-aware `unpublished!` and two tests. The drpodcast.nu list arrives
a round trip sooner on every new query, with up to 20 checks fewer when queries
share series, which also frees the proxy for prefetches. Estimated.

**Risks.** A newly published DR series shows as unpublished for up to the
cache's age. Caching a failed check would hide a series, so failures stay out.

**Sources.** [RFC 9111, section 4.3](https://www.rfc-editor.org/rfc/rfc9111.html#section-4.3),
[Fetch's cache modes](https://fetch.spec.whatwg.org/#concept-request-cache-mode).


### 29. Give the lock screen the server's artwork thumbnails, not the original

*Network · 3 h · gain 2 · score 8 · confidence medium*

**Problem.** At each Play, `lock-screen!` sets a MediaMetadata whose artwork is
the feed's original image (`elektrolyt/player/audio.cljs:144-155, 319`;
`podcast/app/episode.cljc:127`), for most feeds a single image of 3000 px.
The `player/playback` function sends only the chapters' artwork through the proxy
(`elektrolyt/player.cljc:162-198`). The browser fetches the original for the
lock screen, the notification or the system's media controls while the audio
starts. On a phone that competes with the first audio bytes, and it goes
straight to the artwork host, while the app sends its own images through its
server. With curl, 12 feeds' itunes:image ranged from 70 KB to 7.81 MB, four of
them 0.8 MB or more. The server already makes thumbnails
(`elektrolyt/ui/artwork.cljc:16-40`).

**Fix.** In `player/playback`, next to `with-proxied-chapters`, replace each
http(s) image in the metadata's artwork with the thumbnails of
`ui.artwork/thumbnails` at 256 and 512 px, each with sizes "256x256" or
"512x512", since podcast artwork is square. Leave data and object URLs alone.
Consider adding 1024 to `ui.artwork/widths` for iOS's large Now Playing view;
the server checks the width against the same list
(`elektrolyt/server/artwork.clj:49, 64-74`). Cite the Media Session spec and the
Pocket Casts widths already quoted in ui.artwork. Add a unit test of the
playback's metadata, and an e2e check in player.spec.mjs that
`navigator.mediaSession.metadata.artwork[0].src` starts with /artwork after
Play.

**Effort and gain.** A pure function with tests, an e2e assertion, a comment
and a look at a phone's lock screen. It removes a parallel download of 0.3 to
7.8 MB at every Play where media controls show artwork, and brings the lock
screen in line with the app's privacy. The bytes are measured, the time to
'playing' isn't, and whether desktop Chrome fetches the artwork eagerly is
unverified.

**Risks.** A 512 px image may look soft in iOS's full-screen Now Playing on 3x
screens, which argues for 1024. A small original yields an image smaller than
its sizes claim. The first request for a new artwork waits while the server
scales it.

**Sources.** [the Media Session spec](https://w3c.github.io/mediasession/),
[Pocket Casts' PodcastImage.kt](https://github.com/Automattic/pocket-casts-android/blob/cedff55/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/images/PodcastImage.kt).


### 30. Cache what rows derive from URLs and ids: thumbnails, srcsets, hashes, slugs

*Rendering · 3 h · gain 2 · score 8 · confidence low*

**Problem.** Every render recomputes, for every row, values that depend only on
a URL or an id.

- Each artwork img without sizes from the feed, which is most feeds, gets a URL
  Standard parse through wary-fetch's `web-url?` and four percent-encodings
  (`elektrolyt/ui/artwork.cljc:28-37, 60-63, 77-91`;
  `wary_fetch/url.cljc:20-25, 236-259`).
- Each episode link gets a SHA-1 and a slug (`elektrolyt/ui/routes.cljc:120-133`).
- Each Queue or Inbox button id gets a pr-str and a SHA-1
  (`elektrolyt/ui/rows.cljc:7-11`), eight per Inbox row with the neighbour's
  focus targets (`elektrolyt/library/inbox/ui.cljc:52-84`).

That's probably milliseconds per render on big Library, Inbox and Queue pages,
which render every playback second. Not measured.

**Fix.** Add a bounded single-argument cache to ui/memo.cljc: a JS Map with
oldest-first eviction past about 2,000 entries in cljs, and an atom on the JVM.
Use it for `artwork/thumbnails` by URL string, returning the same vector each
time, and cache the srcset string with it. A JS Map keys a CLJS vector by
identity, so key the cache of `control-id` by a string built from the two
guids, or keep a CLJS map in a volatile with the same bound. Cache `short-hash`
and the slug in ui.routes, for `episode-href` and `rows/control-id`. Leave
`facts/source-host` alone, since it runs only for supplemented episodes. Add
unit tests that each cached function equals the uncached one and that the cache
stays within its bound, and a STATE.md row beside the ui.memo slots.

**Effort and gain.** One cache helper, four call sites, tests and a STATE.md row.
It cuts repeated per-row work on the longest pages at every render. Not
measured.

**Risks.** A JS Map keyed on CLJS values by identity would almost never hit, so
the keys must be strings. The cache is state outside the store and needs its
row.

**Sources.** [MDN on Map](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks).


### 31. Build the Show page's episode rows lazily, only for the episodes it lists

*Rendering · 3 h · gain 2 · score 8 · confidence low*

**Problem.** A show's page turns every episode into a row
(`elektrolyt/show.cljc:43-54, 72-87`), though it lists 20 at first
(`show.cljc:30-33`; `elektrolyt/show/ui.cljc:194-219`). So the first open of a
show with thousands of episodes is slower than it needs to be, and so is every
rebuild, which for a library show comes at every change of the profile until #4
narrows the key. In the same path, podcast-clj's `resolve/episodes` sorts with a
key that it recomputes at every comparison, `serial?` included
(`podcast/feed/resolve.cljc:944-948, 975-976`), which is likely a bigger cost.

**Fix.** In `view->page`, keep `:episodes` as a lazy `(map episode-row
episodes)`, realised 32 at a time and only as far as `(take shown …)` reaches.
Add `:episode-count`, and compute `:latest` from `(keep resolve/published
episodes)`, which only reads an inst. In show/ui.cljc, read `:episode-count`
instead of `(count rows)` in the status line, the failure notice and Show more.
Tests in show_test.cljc that the first rows, the latest date, the cadence and
the count don't change; run show.spec for Show more and its focus. Separately,
have `resolve/episodes` compute each item's listening key once.

**Effort and gain.** A small change of the page's shape in two files, with
tests. A faster first open and rebuild of long shows, on an occasional and
unmeasured path, where the sort may cost more.

**Risks.** Anything that realises the whole seq, e.g. count or last on
`:episodes`, undoes the gain.

**Sources.** [web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[Clojure on laziness](https://clojure.org/reference/lazy).


### 32. Fetch artwork 4 at a time and scale it 2 at a time

*Network · 3 h · gain 2 · score 8 · confidence low*

**Problem.** Each of the thumbnail handler's two jobs is held through the whole
download (up to 20 MB, with a 30 s timeout) and then the scale
(`elektrolyt/server/artwork.clj:49-59, 260-280`). So a cold grid fills about
two artworks per fetch and scale, a slow host can tie up a job for 30 s, and a
request that waits 10 s for a job is redirected to the 3000 px original with
no-store (`artwork.clj:326-328, 397-401`). A colour load that meets that
redirect then fetches the original through the proxy
(`elektrolyt/ui/artwork/colors.cljs:39-52`). Waiting requests hold the browser's
HTTP/1.1 connections, which the proxy's fetches share. You notice it after an
OPML import or on a new device, when the Library grid, which isn't lazy
(`elektrolyt/library/ui.cljc:50`), asks for every thumbnail at once.
Unmeasured.

**Fix.** Raise `:max-jobs` to 4 and add `:max-scales 2`, with a second semaphore
that only the `image/thumbnails` call in `fetched` takes (`artwork.clj:245`).
Each job still holds one of `:max-jobs` from the fetch to the store, so the
bytes held stay bounded at 4 × `:max-bytes`; don't release it between download
and scale. Rewrite the `:max-jobs` docstring and the heap reasoning in the
routes' docstring (`elektrolyt/server.clj:250-255`): 4 proxy bodies of 32 MB,
4 fetches of up to 20 MB and 2 decodes of about 20 MB come to about 248 MB of
320. Note that more than about 4 jobs buys little, since a browser opens about
six HTTP/1.1 connections to the server. Add JVM tests that two hanging
downloads don't keep a third from its job, and that at most `:max-scales`
scales run at once. Time a cold Library grid of about 50 shows first.

**Effort and gain.** Two semaphores, two docstrings, a comment and two JVM
timing tests. It could fill a cold grid a few times faster on occasional
paths, though on a slow link the bandwidth caps it.

**Risks.** The worst-case heap rises from about 208 to 248 MB of the 320 MB
budget, which no -Xmx enforces. More parallel fetches to one CDN may meet rate
limits.

**Sources.** [Chrome DevTools' network reference](https://developer.chrome.com/docs/devtools/network/reference),
[MDN on HTTP/1.x connections](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Connection_management_in_HTTP_1.x),
[Java's Semaphore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html),
[imgproxy's options](https://docs.imgproxy.net/configuration/options).


### 33. Time a subscription's indexing stages in podcast-clj's browser bench

*Measurement · 3.5 h · gain 2 · score 8 · confidence high*

**Problem.** The browser bench in podcast-clj times only the parse
(`dev/browser-bench/browser_bench.cljs:44-60` in podcast-clj), and its JVM bench
neither times subscribe nor says anything about a phone or how the cost grows
with the index (`dev/bench.clj:99-108, 198-202`). Since the subscribe fix,
what's left on the page has never been measured on its own: `built-for`'s id
check, `without-feed`'s scan of every id and search/add
(`podcast/app/library/impl.cljc:186-203`, `podcast/feed/search.cljc:283-289`).

**Fix.** Add stages:

- The worker's share: `feed-documents` and `with-search-documents`.
- The page's share: subscribe with those documents built, into an empty
  library and into one of 50 sweep feeds.
- The fallback: subscribe without them.
- Unsubscribe, and `refreshed` with one changed feed, in the 50-feed library.

Build the library once without throttling, then set THROTTLE=4 for the timed
runs, which needs a small change to run.mjs. Record cold runs apart, on the
sweep's largest feeds, at b8f4aab and before it in a git worktree.

**Effort and gain.** Five stages and a prepared library in an existing harness;
a dev bench needs no app tests. It measures what's left of the main freeze
where it's being fixed, including how `without-feed` grows with the index.

**Risks.** Chrome only, with JIT warm-up, hence cold runs apart.

**Sources.** [the DevTools Protocol's Profiler](https://chromedevtools.github.io/devtools-protocol/tot/Profiler/),
[setCPUThrottlingRate](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setCPUThrottlingRate).


### 34. Unsubscribe after the paint, and keep the page's feed in view

*Perceived · 3.5 h · gain 2 · score 8 · confidence medium*

**Problem.** Unsubscribing does its library work in the click, with no paint
first (`elektrolyt/show/feeds.cljs:326-333`): dequeueing and dismissing the
show's episodes, a scan of every id in the index, and drop-in-search's removal,
which can rewrite or merge segments (`podcast/app/library.cljc:298-311`;
`podcast/app/library/impl.cljc:222-239`). The bookmark stays filled until that,
two renders and the replace-state are done, for an unmeasured time. Then the
library drops the feed. After a reload, or for any subscription whose feed
isn't also in `:feeds`, the page falls back to 'Show' and 'Loading the show…'
(`elektrolyt/show.cljc:166-188`), since `load-show!` skips `:feeds` for a
library show (`feeds.cljs:240-268`). The e2e test never reaches that state,
because it visits the show before subscribing (`test/e2e/show.spec.mjs:4-25`).

**Fix.** Add an `:unsubscribing` set beside `:subscribing` in
show.subscription. The status function in ui.subscription checks it before
`:subscribed`, since the library still has the show during the wait
(`elektrolyt/ui/subscription.cljc:16-23`), and the button then renders the thin
bookmark, aria-disabled, with 'Unsubscribing from …'. The new `unsubscribe!`
takes the route's guid first, swaps in the mark, seeds `(:feeds url)` from the
library's entry with `show/whole` and checked? false so that `load-show!` checks
it again (`feeds.cljs:258-260`), and calls replace-state. After
`runtime/painted!`, it calls `change!`, which clears the mark, and `save!`. Add
the key to STATE.md's `:subscribing` row, a unit test, a Portfolio scene, and an
e2e test that reloads before unsubscribing with the feed's route aborted and
expects the episodes to stay.

**Effort and gain.** It mirrors `subscribe!`, plus the seeding, the order in
the status function, a scene and two tests. Unsubscribing is occasional; the
empty page after a reload is visible, and long when the cache has no copy.

**Risks.** The library's copy may be cut by max-items or keep-fn until the
check fetches again. The wait adds a frame, or 100 ms in a hidden tab.

**Sources.** [web.dev on INP](https://web.dev/articles/inp),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[React's useOptimistic](https://react.dev/reference/react/useOptimistic),
[Remix on pending UI](https://v2.remix.run/docs/discussion/pending-ui/).


### 35. Draw a short, delayed busy line under the status of slow loads

*Perceived · 3.5 h · gain 2 · score 8 · confidence medium*

**Problem.** While a feed, a show, a search, the picks, a transcript or an
episode loads, the only sign is a line of static grey text: 'Loading
episodes…', 'Loading the show…', 'Searching…', 'Looking for episodes like it…',
'Loading the transcript…' or 'Loading the episode…'
(`elektrolyt/show/ui.cljc:149-161, 206, 224, 337`;
`elektrolyt/search/ui.cljc:88, 103, 170-175`;
`elektrolyt/library/picks/ui.cljc:63`; `elektrolyt/episode/ui.cljc:245-246,
274`). Nothing on the page moves, and these network waits can last seconds. The
CSS has no keyframes at all (grep).

**Fix.** Add a class, e.g. busy, to the loading branch only of those
paragraphs. In layout.css, give .busy position: relative and place .busy::after
absolutely at the paragraph's foot, 2px high and 30% wide. In look.css's
no-preference block (`public/css/look.css:1023-1044`), add keyframes that move
it with translateX from 0 to 233%, so it stays inside the paragraph. Start it
with animation-delay: 1s from opacity 0, so that loads under a second never
show it, and run about four 1 s alternate iterations, so the motion stops within
5 s (WCAG 2.2.2, since these lines sit beside content the listener can use).
Use background-color: currentColor. In forced colours, either let it vanish or
opt out with CanvasText, as the switch does (`look.css:1006-1015`). Optionally,
put the same line on the 'Subscribing to …' bookmark. Add Portfolio scenes for
each loading state, an e2e test that the ::after's animation-name is set under
no-preference and none under reduce, and checks of forced colours and 320 px.

**Effort and gain.** A class on six branches, about 25 lines of CSS, scenes, one
e2e spec and the checks. Polish: the waits look alive, but nothing gets faster.

**Risks.** Endless motion beside usable content fails WCAG 2.2.2 after 5 s, so
the iterations stay bounded. A class left on a done state keeps the line
showing, which the view tests should check.

**Sources.** [web.dev on animations](https://web.dev/articles/animations-overview),
[NN/g on progress indicators](https://www.nngroup.com/articles/progress-indicators/),
[Understanding WCAG 2.2.2](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html),
[MDN on forced-colors](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/forced-colors),
[MDN on prefers-reduced-motion](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion).


### 36. List the cache database once for the artwork colours read at start

*Startup · 3.5 h · gain 2 · score 8 · confidence medium*

**Problem.** In like-minded, `cache/fetch!` lists every document in the store on
each call (`like_minded/cache.cljc:107-130`;
`like_minded/store/indexeddb.cljc:123-135`). It then runs `entries`, a prefix
test on every name and a regex, parse-long and sort on the cache's own
(`cache.cljc:42-53`), all to find one hash. The app keeps five caches in one
database, up to 1,220 entries (`elektrolyt/runtime/cache.cljs:14-52`). Before
the first render, `kept-colors!` runs one `fetch!` per artwork on the page, all
at once (`elektrolyt/ui/artwork/colors.cljs:92-103`;
`elektrolyt.cljs:475-481`): 5–30 listings on Home and hundreds on a big Library
page, each going through the whole listing while the page is blank. Unmeasured.

**Fix.** In like-minded, add `cache/fetch-all! [cache ks]`, with an arity that
takes a time: hash the keys, list once, run `entries` once, index the newest
unexpired entry by hash, and fetch only those bodies, in parallel. It returns a
map from each key to its value and time, leaving out misses and bodies that
don't decode, as `fetch!` does, and `fetch!` becomes its case of one key. Add
tests on the memory store that count list calls, and expiry and decode failures
on both platforms. Release like-minded (bumping its deps and running its CI
steps locally) and pin it. In the app, add `app-cache/fetch-all!` with the same
catch to nil, and use it in `kept-colors!`.

**Effort and gain.** One like-minded function with tests and a release, then a
wrapper and one call site. It removes all but one listing before the first
paint: perhaps a few tens of ms on Home, and a few hundred at a start on a big
Library page. The path is certain, but its cost isn't timed.

**Risks and principles.** A listing reused across keys may name an entry deleted
since, which reads as a miss, as `fetch!` already allows. It's a fix at the
source, in like-minded.

**Sources.** [IndexedDB's getAllKeys](https://www.w3.org/TR/IndexedDB/#dom-idbobjectstore-getallkeys),
[MDN on getAllKeys](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/getAllKeys),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks).


### 37. Find an outside show's repeated runs in the worker for its picks

*Main thread · 4 h · gain 2 · score 8 · confidence medium*

**Problem.** On the page of a show outside the library, an open More like this
makes `recommend` call `repeated-runs` on the whole feed at every call
(`podcast/app/library.cljc:1497-1501`; `elektrolyt/library/picks.cljc:134-152,
193-210`): in the click that opens the section, again when the whole feed
replaces the head, and at each change of the picks' key. It reads the HTML of
every episode's notes (`podcast/feed/search.cljc:116-125`), which b8f4aab's
message names as most of the cost of building documents. So it's plausibly most
of the 171 ms measured for Podcasting 2.0, though nobody has measured it alone.

**Fix.** In podcast-clj's `recommend`, compute `repeated-runs` only for a feeds
entry without `:repeated`, document the key as optional, and test that the
picks are the same either way. In the app, add a data flag, e.g. `:repeated?
true`, to the Show page's whole fetch and check (`fetch-whole!` and
`check-show!`), but not to `whole-feed!`, which a subscription uses and which
would then wait for runs that `with-search-documents` works out again
(`elektrolyt/show/feeds.cljs:33-39, 281-288`). With the flag, the worker puts
the runs in the answer before it encodes it
(`elektrolyt/worker/fetching.cljs:165-172`). Then `show/whole` and `show/kept`
keep them (`elektrolyt/show.cljc:267-271, 290-300`), so a cached show has them
too, and `picks/outside` passes them on. Update STATE.md's rows for `:feeds` and
the cache.

**Effort and gain.** A cond-> and a test in podcast-clj, a flag in the worker,
two keys in show.cljc and `picks/outside` with tests, STATE.md and one profile.
An estimated 100 ms or more out of an occasional click and out of the renders
while the section is open. After #45 it's worth about 1.

**Risks and principles.** The Show page's whole feed arrives later by the time
the runs take in the worker, so measure it. The `recommend` change is a pure
function of podcast-clj, while the cache entry and the flag are app policy.

**Sources.** [Surma, When should you be using workers?](https://surma.dev/things/when-workers/),
[MDN on using web workers](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API/Using_web_workers).


### 38. Hold the Show and Episode pages' shape with delayed skeleton rows while a feed loads

*Perceived · 4 h · gain 2 · score 8 · confidence medium*

**Problem.** From a search result the Show page has its intro at once
(`elektrolyt/show.cljc:214-223`), but the episode list is only 'Loading
episodes…' until the feed's start arrives, and then 20 rows push the rest of
the page down (`elektrolyt/show/ui.cljc:200-215`). Without a page at all (a
deep link, a Podcast Index lookup, or an outside show whose cache read is
pending, `elektrolyt/show/feeds.cljs:204-221`), there's only the heading and
'Loading the show…' (`show/ui.cljc:323-339`), and everything else arrives at
once. The Episode page does the same on a deep link
(`elektrolyt/episode/ui.cljc:266-281`).

**Fix.** While the status is loading with no rows, render about five
aria-hidden li.skeleton rows after the status line, in the .show-episode grid
(`public/css/layout.css:1187-1220`). Without a page, render a skeleton intro,
in the dominant colour if a known result gives one, and do the same on the
Episode page. Sizes go in layout.css and colours in look.css, with an outline
in the forced-colors block so that the bars don't vanish. Reveal them after an
animation delay of about 300 ms, as `layout.css:995-997` already delays a
reveal, fading in only under no-preference and with no shimmer. Add Portfolio
scenes of each loading state, and an e2e test with the feed delayed 2 s that
expects the skeleton out of the accessibility tree and the status announced.

**Effort and gain.** Two views, skeleton CSS in both layers, scenes and an e2e
test. Most feed starts probably arrive within a second, where NN/g advises no
indicator, so the gain is held shape on slower and deep-link paths. Load times
are unmeasured.

**Risks.** The CSS delay moves the flash to loads of about 350 ms.

**Sources.** [NN/g on skeleton screens](https://www.nngroup.com/articles/skeleton-screens/),
[NN/g on response times](https://www.nngroup.com/articles/response-times-3-important-limits/),
[Learning Equality on loaders](https://design-system.learningequality.org/loaders),
[Stripe on loading](https://docs.stripe.com/stripe-apps/patterns/loading).


### 39. Keep the refresh's indexing from holding up the listener's worker requests

*Main thread · 4 h · gain 2 · score 8 · confidence medium*

**Problem.** Each tab has one web worker (`elektrolyt/worker/fetching.cljs:245-265`),
whose one thread serves the refresh, the Show page's whole feed and the
subscription's search documents (`fetching.cljs:184-203`). A refresh that found
new episodes ends with one synchronous task that finds the repeated runs of
every changed feed and encodes each one (`podcast/app/library.cljc:722-734`). A
listener's request that reaches the worker meanwhile waits. 'Loading
episodes…' stays on an outside Show page, since only its first 20 episodes come
from the page's own fetch, and a subscription finishes late, just when a
listener taps a show after opening the app. Modelled from per-feed costs
measured in Node, a morning refresh with five changed feeds holds it about 1 s
on an M1 and 4 s on a phone; a browser hasn't measured it. The proxy's four
slots against the refresh's eight fetches may matter as much (#18).

**Fix.** Stage 1, in podcast-clj: handle the changed feeds at the end of
`fetch-due!` one at a time, yielding a macrotask between them in ClojureScript
(a small yield in util.async, a no-op on the JVM), so that a listener's message
and its parse can run between feeds. Cite HTML's event loop, and test that the
result is the same and that a message posted during the tail is handled before
it ends. Release it with the other changes. Stage 2, only if a trace still shows
waits: one client per lane in worker.fetching, `{:refresh client :listener
client}` from the same script, the listener's started when a refresh starts and
the refresh's ended when the refresh settles. Add a STATE.md row and an e2e test
that checks page.workers().

**Effort and gain.** Stage 1 with its tests is about 4 h, and stage 2 adds about
5. It shortens an estimated 1–4 s wait, but only when a listener opens an
outside show or subscribes during a refresh with changes. Stage 1 bounds the
wait to the largest single changed feed.

**Risks.** It touches the file you're changing for subscriptions, so land it
after that work. A second worker costs a heap of its own and a second
evaluation of the script, and competes for CPU on low-end phones.

**Sources.** [HTML's event loop](https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model),
[HTML on workers](https://html.spec.whatwg.org/multipage/workers.html#scope-2),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[Playwright's page.workers()](https://playwright.dev/docs/api/class-page#page-workers).


### 40. Let the browser skip off-screen rows, and lazy-load the Library tiles past the first dozen

*Rendering · 4 h · gain 2 · score 8 · confidence low*

**Problem.** The Library lists every show, the Inbox and the Queue every
episode, and an open transcript every paragraph
(`elektrolyt/library/ui.cljc:76-79`, `elektrolyt/library/inbox/ui.cljc:126-140`,
`elektrolyt/library/queue/ui.cljc:121-162`, `elektrolyt/episode/ui.cljc:251-259`).
The browser styles, lays out and paints all of them at the first render and
whenever their layout is invalidated, since the CSS has no content-visibility.
The Library's tiles also load every artwork eagerly, unlike every other list
(`library/ui.cljc:50`), and at 2x their 160px sizes pick the 512 px thumbnail.
So after an import the visible tiles compete with dozens of off-screen ones for
connections and the server's two artwork jobs.

**Fix.** In layout.css, add content-visibility: auto with contain-intrinsic-size:
auto and an estimate to .library-shows > li (separate estimates for the list and
the grid, and the switch at 37.5em), .inbox > li, .queue > li, .search-results >
li and .transcript > p. That always turns on paint containment, which would clip
the 2px focus ring 2px out (`public/css/look.css:76-81`) and the lift shadows
(`look.css:353-373`). So give the rows inline padding offset by a negative
margin, since overflow-clip-margin isn't reliable everywhere. Cite the
containment spec and web.dev. In library/ui.cljc, pass each tile's index to
`show-item`, and set loading="lazy" from about the 13th. A pure view can't
follow the layout, so the count must be large enough for a desktop grid's first
screen. Write e2e tests that, after Dismiss, Remove or Move, focus moves to rows
that were off-screen and they scroll into view, and that find-in-page reaches
skipped rows. Check focus rings at the rows' edges in three browsers, and the
grid at 320 px and on a desktop.

**Effort and gain.** A few CSS rules and one attribute, padding for the focus
rings, and checks of scrolling, focus, find-in-page and clipping in three
browsers. The first paint and relayout of long lists get faster, with fewer
competing image requests. Unmeasured, and the JS cost of a render doesn't
change.

**Risks and principles.** Clipped focus rings would fail WCAG 2.4.7, so rule
that out first. Each row becomes a stacking context and a containing block for
its visually hidden spans. Safari has long lacked scroll anchoring, so wrong
estimates jump when scrolling up past rows that never rendered. Support starts
at Chrome 85, Firefox 125 and Safari 18. The rules go in layout.css.

**Sources.** [web.dev on content-visibility](https://web.dev/articles/content-visibility),
[CSS Containment 2 on content-visibility](https://drafts.csswg.org/css-contain-2/#content-visibility)
and [on paint containment](https://drafts.csswg.org/css-contain-2/#paint-containment),
[MDN on contain-intrinsic-size](https://developer.mozilla.org/en-US/docs/Web/CSS/contain-intrinsic-size),
[caniuse on overflow-clip-margin](https://caniuse.com/mdn-css_properties_overflow-clip-margin),
[caniuse on overflow-anchor](https://caniuse.com/css-overflow-anchor),
[web.dev on lazy-loading images](https://web.dev/articles/browser-level-image-lazy-loading),
[web.dev on lazy-loading and LCP](https://web.dev/articles/lcp-lazy-loading).

### 41. Measure drop-in-search's merges, then cap them so one add never rewrites the whole index

*Main thread · 6 h · gain 2 · score 6 · confidence low*

**Problem.** Every add and remove merges segments in the same task. The balanced
policy merges the last two segments while the last holds at least half as many
as the one before, so an add can cascade like a binary carry and rewrite most
of the index once per doubling (`drop_in_search/indexing.cljc:79-92, 187-191`).
The pruning step rewrites, in one go, any segment with more deleted than live
documents (`indexing.cljc:101-118, 122-144`), which a big segment reaches as
refreshes replace its feeds' documents. A merge copies every live posting, id
and stored value, and the next save hashes and encodes the result
(`drop_in_search/segment.cljc:239-255, 717-739`). These are rare but unbounded
spikes on top of the add, and they may be part of the gap between the 281–674
ms measured here and the seconds you see.

**Fix.** First time search/add and remove on podcast-clj's 15.6k-episode lab
library, and look for `merged-from` and `combined` in a trace of your own
subscribe. If merges show up, give the index a default `:max-merged-docs`, e.g.
8192, that `balanced` won't merge past, as Lucene's TieredMergePolicy bounds its
merges. Give `pruned` a bound of its own, the share of deleted documents a
segment may hold before it's rewritten, as Lucene's deletesPctAllowed does. The
one-off `indexed` and `compacted` keep merging everything. Test on both
platforms that the results don't change and that the segment count and merge
size stay bounded.

**Effort and gain.** A measurement, a policy change with tests and a release.
It removes occasional large spikes in big libraries, though a cap of 8192 halves
the worst case only in a library of about 16k documents. Unmeasured.

**Risks and principles.** More segments make queries slightly slower and add
IndexedDB documents, and more deletes held back make the index larger. The fix
belongs in drop-in-search.

**Sources.** [Lucene's TieredMergePolicy](https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/index/TieredMergePolicy.html),
[Lucene's ConcurrentMergeScheduler](https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/index/ConcurrentMergeScheduler.html).


### 42. Read only new index segments when catching up with another tab

*Main thread · 7 h · gain 2 · score 6 · confidence medium*

**Problem.** When another tab writes a library whose index changed (a
subscription, an unsubscription or a refresh with changed feeds), this tab
catches up on the channel's message, when it's visible again, on pageshow and
before each refresh of its own (`elektrolyt/library.cljs:217-247`;
`elektrolyt/library/refresh.cljs:164`). It fetches and decodes every segment
again (`podcast/app/library.cljc:2097-2104, 2128-2147`), though segments are
named by their content and this tab holds most of them. Each segment's postings
are decoded and encoded again, and each record is read as EDN
(`drop_in_search/ciff.cljc:794-881`), in one task, often right as the listener
switches tabs. A save conflict does the same
(`podcast/app/library.cljc:2335-2339`). A listener with one tab never meets it.

**Fix.** In podcast-clj's `needs`, leave out the segments that the held index
has by name. In `from-saved`, build the index from the held Segment objects and
the new ones, applying each segment's deleted set from the manifest with
`segment/with-removed` (`drop_in_search/segment.cljc:399-402`), through a
`:held` option of `ciff/read-files` in drop-in-search. Check that the manifest's
fields agree with the held index's, or else read everything. Test on both
platforms that a catch-up after one subscription reads one segment and equals a
full read, with deletes and a changed numbering of fields.

**Effort and gain.** Changes in two libraries, with equivalence tests and two
releases. A catch-up then costs the new segments instead of the whole index,
for listeners with several tabs and on conflicts. Occasional and unmeasured.

**Risks and principles.** Held segments with new deletes must be updated, and
the field numbering must agree, or the read falls back to the whole index. The
fix belongs in the libraries.

**Sources.** [Lucene's index package](https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/index/package-summary.html),
[HTML on BroadcastChannel](https://html.spec.whatwg.org/multipage/web-messaging.html#broadcasting-to-other-browsing-contexts).


### 43. Build a benchmark library of 50 shows for repeatable profiling

*Measurement · 7 h · gain 2 · score 6 · confidence medium*

**Problem.** The e2e fixtures serve one show of three episodes
(`test/e2e/fixtures.mjs:23-34`). Nothing reproduces the seconds you see with
your own library, or compares a change before and after on the same data, and
library-sized costs such as `without-feed`'s scan or a refresh of many feeds
appear only with many large shows. The sweep corpus in podcast-clj has the mix
of sizes: 11 feeds over 2,000 items, 67 of 300–800 and 424 under 200
(`dev/corpus.clj:23-25, 227-229` in podcast-clj, with the corpus kept outside
git).

**Fix.** Add test/e2e/bench/ with a manifest of 50 sweep feeds chosen by item
count (5 over 2,000, 15 of 300–800 and 30 under 200), committed as an OPML of
their real URLs. A setup project does three things:

- It routes /proxy for those URLs to the files under BENCH_CORPUS, media to
  silence.mp3, /artwork to a fixture image or a 404 in the browser, so the
  server never fetches real artwork, and every other /proxy URL to 404.
- It imports the OPML on Settings (`test/e2e/settings.spec.mjs:20-33`), and
  waits until the Shows list holds 50 items and aria-busy is false. Since
  aria-busy starts false, the count matters (`elektrolyt/library/ui.cljc:75-79`).
- It saves storageState with IndexedDB, which a fixed E2E_PORT lets later runs
  reuse.

A seeded generator writes the same mix of sizes for CI. The same OPML lets you
import the 50 real feeds into the dev app through the real proxy and profile
there without routing. Write a README paragraph on both uses.

**Effort and gain.** The selection, the routing, a setup project, reuse of the
storage state, a generator and restore checks in Firefox and Chrome. It makes
freezes the size of a library reproducible, though no listener feels it.

**Risks.** The snapshot may run to tens of MB and restore slowly in Firefox.
Real feeds' quirks may throw in the page. CI depends on the generator matching
real feeds' shape.

**Sources.** [Playwright's storageState](https://playwright.dev/docs/api/class-browsercontext#browser-context-storage-state),
[Playwright's project dependencies](https://playwright.dev/docs/test-global-setup-teardown#option-1-project-dependencies),
[Playwright's route](https://playwright.dev/docs/api/class-browsercontext#browser-context-route).


### 44. While hidden, skip clock-only renders, render once per task, and catch up when shown

*Rendering · 7 h · gain 2 · score 6 · confidence medium*

**Problem.** With the screen off or another tab in front, the page keeps
rendering in full at every store change, and each render forces a style recalc
through `theme-colored!`'s getComputedStyle
(`elektrolyt/ui/document.cljs:44-67, 90-104`). Nothing anyone sees depends on
those renders: the title and the favicon change only with the route or the
episode (`elektrolyt/ui.cljc:91-105`), and the lock screen follows the
element's events (`play_nice/session.cljs:33-38`). A lock-screen Next and an
autoplay each render the hidden page up to six times in one task before the
next source is set (`elektrolyt/player/audio.cljs:81-99, 165-177, 305-320`). A
batch per frame, as in #12, never flushes in a hidden page on its own, since
browsers pause rAF there.

**Fix.**

1. In player.cljc, add a pure `clock-only?` of the old and new state: every
   top-level key but `:player` identical, `:player` different only in `:media`,
   `:now`, `:elsewhere` and `:buffering?`, and the same shown episode and
   playing state.
2. In runtime.cljs, add a scheduler that runs once after the task
   (queueMicrotask behind a flag).
3. In elektrolyt.cljs, keep `::render` as it is while visible, or #12's batch.
   While hidden, skip clock-only changes and render any other change once after
   the task, which makes a lock-screen Next one render after `media/source!`.
   Skip `::colors` while hidden.
4. At `on-visible!` and pageshow, render once and run `colored!` once.
5. In `load!`, render at once when there's no element, so that a hidden tab
   that cues a player it never rendered finds it.
6. In `:player/unmounted` (`elektrolyt.cljs:211-217`), make `report!`'s change
   of the store in queueMicrotask, so that no render nests and Replicant never
   queues one for a frame (`replicant/dom.cljs:210-235`).

Test `clock-only?` in player_test, count one render for a nexttrack press with
`runtime/visible?` redefined to false, and add an e2e spec that fakes a hidden
visibilityState, fires a recorded nexttrack handler and checks that the clock
catches up when shown. Cite MDN on visibilityState and the paused rAF, and add a
STATE.md row for the pending flag.

**Effort and gain.** A predicate, a scheduler, two watches and two effects, a
catch-up path and the tests, plus checking the two-tab specs under Playwright's
visibility behaviour. After #3, it removes the remaining render a second for
hours of listening with the screen off, and starts the next episode's file
after one render instead of up to six: about 75 ms in dev on a desktop, likely
more on a phone. Battery and latency are unmeasured.

**Risks.** A DOM read in a hidden page that expects a fresh render; step 5
covers `load!`. A missed visibilitychange leaves a stale page until the next
change, hence the render at pageshow. Motion items must keep
`:replicant/mounting` off any render that mounts or unmounts the player.

**Depends on.** [#3](#3-keep-only-the-media-keys-the-views-read-so-playback-renders-once-a-second).

**Sources.** [MDN on requestAnimationFrame](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestAnimationFrame),
[MDN on visibilityState](https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilityState),
[Chrome on the Page Lifecycle](https://developer.chrome.com/docs/web-platform/page-lifecycle-api),
[Chrome on timer throttling](https://developer.chrome.com/blog/timer-throttling-in-chrome-88),
[HTML's event loop](https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model),
[Replicant's dom.cljs](https://github.com/cjohansen/replicant/blob/main/src/replicant/dom.cljs).


### 45. Find the picks after the paint and keep them in the store

*Main thread · 8 h · gain 2 · score 6 · confidence medium*

**Problem.** Home and the Queue page call `library/recommend` while they render,
whenever the picks' memo key changes: the index, the feeds or the queue
(`elektrolyt/library/picks.cljc:89-118`; `elektrolyt/ui/home.cljc:62`,
`elektrolyt/library/queue/ui.cljc:182`). Play on Home or the Queue moves the
episode to the front of the queue (`elektrolyt/player/audio.cljs:359-367`), so
`recommend` runs inside the Play click's render. It also runs in the render of
each refresh that lands, and in the click that opens More like this. It sorts
every heard episode and counts every id in the index
(`podcast/app/library/impl.cljc:794-814, 856-872`). The app's own comment says
tens of milliseconds, unmeasured in a large library. Home's picks are also
worked out before the first render, since `kept-colors!` asks for their artwork
(`elektrolyt/ui/artwork/colors.cljs:92-104`).

**Fix.**

- Keep the picks as data under `:picks` by place (`[:home]`, `[:queue]`,
  `[:show guid]` and `[:episode id]`), each as `{:key k :picks p}`.
- A store watch beside `::colors`, in a runtime namespace, finds the places that
  the new state shows. For each place whose key differs from the kept one, it
  computes after `runtime/painted!`, and swaps the result in only while that key
  is still current. Use a watch rather than a life-cycle hook: Replicant fires
  hooks only on elements that changed (`replicant/core.cljc:345-371, 960-976`),
  and a key in a hook's data would make it compare the index by value.
- Home and the Queue keep showing their last picks while new ones are found.
  Before there are any, the section shows its heading over a block that reserves
  its height, with aria-busy, instead of the text for no picks. Folded sections
  keep 'Looking for episodes like it…' and never show another key's picks.
- The listed function in ui.colors reads `:picks`, `kept-colors!` stops
  computing picks, and autoplay keeps computing directly (`audio.cljs:108`).

Add a STATE.md row, Portfolio scenes for pending, stale and filled picks, unit
tests of the places and keys, and an e2e test that Home's picks and an opened
More like this fill in.

**Effort and gain.** A watch and its effect, four views and the pending block's
CSS, the colours and startup paths, STATE.md, scenes and tests. It takes
`recommend` out of the Play click, out of refreshes landing and out of the More
like this click, and lets Home's first paint skip it. It may be a 3 in your
library, once a profile shows what `recommend` costs.

**Risks and principles.** The picks land a frame after the page, and a
placeholder of the wrong height shifts it. If the watch doesn't compare keys
before it schedules, it can run `recommend` more often than the memo does.
In podcast-clj, `read!` builds a new feeds map on every read
(`podcast/app/library.cljc:2151`), so check whether each catch-up changes the
key. The views stay pure functions of the state and an effect fills it, as
`::colors` does, with one writer for the new fact.

**Sources.** [web.dev on INP](https://web.dev/articles/inp),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[caniuse on requestIdleCallback](https://caniuse.com/requestidlecallback),
[caniuse on postTask](https://caniuse.com/mdn-api_scheduler_posttask),
[MDN on aria-busy](https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Attributes/aria-busy),
[HTML's event loop](https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model).


### 46. Keep only the notes field that resolve/description shows, as the worker parses

*Rendering · 8 h · gain 2 · score 6 · confidence medium*

**Problem.** Every parsed feed keeps each episode's notes in up to four fields
(content:encoded, description, itunes:summary and googleplay:description), which
hosts often fill with the same HTML (`podcast/feed/spec.cljc:415, 448`). Every
reader takes one of them, in the order of `resolve/description`
(`podcast/feed/resolve.cljc:21-29`). The unused copies stay in the heap for
every subscription and outside show. They're encoded in the worker, posted,
decoded, written to IndexedDB and read back at every open. On podcast-clj's
31-feed corpus, 48% of the notes, 40% of all text, sit in fields that
`resolve/description` never picks.

**Fix.** In podcast-clj, add a pure function, e.g. `feed.resolve/lean`, that
keeps on the channel and on each item only the notes key that
`resolve/description` would pick, in the same order, so that an empty
content:encoded still wins. Test on the JVM and in CLJS that
`resolve/description` and the search documents come out the same. In
worker.fetching, apply it to every parsed feed before it's encoded: make
`fetches` always return a `:fetch` and a `:fetch-head`, as lean wrappers around
`podcast/fetch!` and `discovery/fetch-head!`, with DR's supplementing inside
them when alternative feeds are on. Today `fetches` returns nil without them
(`elektrolyt/worker/fetching.cljs:144-172`). Refreshes then compare lean with
lean, and a stored feed from before compares unequal once and is written back
lean. Cite RSS 2.0, the content module and Apple's dropping of itunes:summary.
Add an e2e test that an Episode page whose feed has all three fields still shows
its notes and that an unchanged refresh changes nothing, and a STATE.md note.
Separately, have podcast-clj's `impl/fetched` compare the fresh feed through the
call's keep-fn (`podcast/app/feeds/impl.cljc:112`).

**Effort and gain.** The podcast-clj function with tests (2 h), the keep-fn fix
(1 h), the worker's wiring (2 h), e2e tests (2 h), and comments. It removes about
40% of a typical feed's text from the heap, from IndexedDB and from the Transit
work at every refresh, subscription and open. The share is measured on the
corpus; the effects on heap and time aren't. It's worth 3–4 if #93 finds iOS
kills or GC stalls.

**Risks and principles.** A future reader of description or summary gets nil
when content:encoded is there, e.g. `feed/validate` on stored feeds. Land it
after the subscribe work in podcast-clj. The pure function goes to podcast-clj,
and applying it is app policy in the worker.

**Sources.** [the RSS 2.0 specification](https://www.rssboard.org/rss-specification),
[RSS 1.0's content module](https://web.resource.org/rss/1.0/modules/content/),
[Apple's podcast requirements](https://podcasters.apple.com/support/823-podcast-requirements).


### 47. Keep two of the proxy's four slots for the listener's own requests

*Network · 8 h · gain 2 · score 6 · confidence low*

**Problem.** The proxy's four slots are one fair queue
(`elektrolyt/server.clj:182-195, 260-261`). Refresh fetches, podroll heads
(`elektrolyt/show/feeds.cljs:340-355`), up to 20 drpodcast.nu checks per search
(`elektrolyt/search/directories.cljs:39-47`), colour bytes for Apple artwork and
revalidations of cached shows fill it. So the Show page's feed start, a
subscription's feed or a search can wait behind them. Under HTTP/1.1 the
browser's six connections queue first, so a lane on the server alone would make
it worse: background requests waiting on the server would hold every browser
connection.

**Fix.**

1. Add `:priority` to wary-fetch's `fetch-hop!` (`wary_fetch/browser.cljs:212-244`),
   as 0.16.0 added `:cache`, and send background requests at low priority. Fetch
   priority is in Chrome 102+, Firefox 132+ and Safari 17.2+, and Chrome orders
   requests waiting for a socket by it.
2. Add a second route, /proxy/background, with the same handler. It takes one of
   2 background permits and then one of the shared 4, so the listener always has
   2. A route needs no header, which matters since Firefox overrides a Priority
   header that a script sets.
3. In library.cljs, add a background send, `(proxy/sender "/proxy/background")`
   at low priority, used by the refresh (through an option that is data),
   `check-show!`, `linked-shows!`, `unpublished!` and `proxied-bytes`. Keep the
   browser's count of background requests low with #18.

Add JVM tests that 4 background requests leave 2 slots and that /proxy counts as
foreground, and a wary-fetch test of `:priority`.

**Effort and gain.** A two-level semaphore and a route with JVM tests, a
wary-fetch option and its release, a send with five call sites, comments and
e2e checks. The listener's requests stay out of a queue of background work for
a few seconds after a search or at a refresh. Unmeasured.

**Risks.** A request marked background by mistake slows down. The two routes are
different HTTP cache keys for the same URL, so the refresh and the Show page
stop sharing cached responses. Fetch priority is only a hint.

**Depends on.** [#18](#18-cap-a-refreshs-feeds-in-flight-below-the-proxys-four-slots).

**Sources.** [whatwg/fetch issue 1718](https://github.com/whatwg/fetch/issues/1718),
[web.dev on fetch priority](https://web.dev/articles/fetch-priority),
[MDN on RequestInit's priority](https://developer.mozilla.org/en-US/docs/Web/API/RequestInit#priority),
[Chromium's client_socket_pool_manager.cc](https://chromium.googlesource.com/chromium/src/+/HEAD/net/socket/client_socket_pool_manager.cc).


### 48. Read an episode's MP3 tags without their artwork, after the element plays

*Network · 11 h · gain 3 · score 6 · confidence medium*

**Problem.** Every Play (`elektrolyt/player/audio.cljs:305-320`, before
`media/source!`), every load with a restored episode (`audio.cljs:576-580`) and
every Episode page (`elektrolyt/show/feeds.cljs:375-381`) reads the media file's
whole ID3 tag through /proxy. To skip a tag, podcast-clj asks for all of it,
even to sniff the format, and `read-front` slices all of it
(`podcast/format/media/id3.cljc:17-29, 131`). For shows with a large embedded
cover or PRIV frame, that's megabytes through the server just as playback
starts: among 14 feeds, curl found tags of 1.36 MB, 3.94 MB (The Daily, one PRIV
frame) and 5.64 MB (Darknet Diaries, a cover). FFmpeg, which Chrome uses, reads
those bodies as it opens the file, so the two downloads compete. Then the main
thread parses the megabytes, and the store keeps the cover's bytes for up to 21
episodes (`elektrolyt/episode.cljc:138-141`), though the app uses only the
chapters and the duration.

**Fix.** Stage 1, in the app (about 2 h): in `load!`, don't read the files in
the click's task, but once `changed!` gets a media state that plays and doesn't
buffer, or failing that at idle after the src is set. The restored episode
reads at idle after the first paint, so its chapter buttons still appear before
Play. That ends the competition at every Play, for every show.

Stage 2, at the source (about 9 h):

- In id3-clj, read a tag's frame layout from the frame headers alone, falling
  back to the whole tag for ID3v2.3's whole-tag unsynchronisation.
- In podcast-clj, have the skip and `read-front` fetch only the bodies they
  read (text frames, TLEN, and CHAP and CTOC with their subframes), skipping
  PRIV, GEOB and APIC unless a new `:artwork?` option asks for it, and have the
  sniff read only the tag's end from its header. Thread `:artwork?` through
  `read-files!`, and leave the artwork out of the result when it's false.
- In the app, pass `:artwork? false`.

Add fixtures with a 2 MB APIC or PRIV before and after CHAP frames, an app test
for when the read is due, and an e2e test that checks through the /proxy route
that under 200 KB is asked for. Release id3-clj, podcast-clj and the app once,
bottom-up.

**Effort and gain.** Stage 1 is about 2 h and stage 2 about 9 h across three
projects. Stage 1 stands alone, and #49 needs only it, so you can build it
first. For shows with tags of several megabytes (3 of 14 sampled, The Daily
among them), Play stops downloading the tag twice at once, and Episode pages
and the restored start stop moving and parsing those megabytes. The bytes are
measured, the time to 'playing' isn't.

**Risks and principles.** ID3's quirks (unsynchronisation, extended headers,
compressed or encrypted frames, non-syncsafe v2.4 sizes) need the existing
fallbacks. With the deferral, chapters arrive a moment after playback starts.
The phone on cellular waits for a server that a phone can reach
(`elektrolyt/server.clj:37-49, 280`). The byte savings go to id3-clj and
podcast-clj, and the deferral is an effect in player.audio.

**Sources.** [the ID3v2.4 structure](https://id3.org/id3v2.4.0-structure),
[FFmpeg's id3v2.c](https://github.com/FFmpeg/FFmpeg/blob/master/libavformat/id3v2.c),
[FFmpeg's demux.c](https://github.com/FFmpeg/FFmpeg/blob/master/libavformat/demux.c).


### 49. Move the library's play step and the no-op add out of a Play click's task

*Main thread · 4.5 h · gain 2 · score 6 · confidence low*

**Problem.** A Play click renders the whole page four or five times in its task
before the src is set and `play()` is called
(`elektrolyt/player/audio.cljs:305-320, 359-370`):

- It applies the episode's add even when the library has the episode. The
  `added` function always returns an operation (`audio.cljs:353-357`), and
  though the operation leaves the library as it is
  (`elektrolyt/library/operations.cljc:46-48`), `change!` still queues it and
  swaps (`elektrolyt/library.cljs:197-206`).
- The step `[:play id]` renders again, and on Home and the Queue its change to
  the queue makes the picks recompute.
- Then `load!` swaps `:player`, and its read of the files swaps again.

Chrome starts loading a new src only in a later task, so all this delays the
redirect chain and the player's paint, wherever in the task the src is set.

**Fix.**

1. Make `added` return nil when `library/playback` has the episode, so that
   `change!` does nothing. That helps `:queue/add` too (`elektrolyt.cljs:298`).
2. For an episode in the library, call `play-id!` first, which renders once to
   mount or update the player and then sets the src and calls `play()` in the
   click, as Safari's first play needs. Run the step `[:play id]` after
   `runtime/painted!`, passing the click's time as `:at`, so that the operation
   carries the listener's time.
3. For an outside episode, keep the add before `play-id!`, since
   `player/playback` needs the episode in the library, and defer its step the
   same way.
4. Leave the read of the files to #48's stage 1.
5. Guard the gap: a progress report or 'ended' that comes before the deferred
   step applies the step first.

Add a unit test that the deferred step carries the click's time, and an e2e
test in queue.spec.mjs that Play still moves the episode to the front of the
queue, also after a reload.

**Effort and gain.** The nil from `added`, the new order with its guard, two
tests and a Safari check that the first play still counts as a gesture. With
#48's deferral, the click's task drops from four or five renders to one, and
loses the picks on Home and the Queue: roughly 40–100 ms in dev before the
network starts. That's small against a redirect chain of 0.3–1.9 s, but it's
the most common action. Unmeasured, and #12 would take part of it.

**Risks.** For one frame the queue shows the old order. The reporter's first
report can land before the deferred step, so the step must be applied first or
ordered by time.

**Depends on.** [#48](#48-read-an-episodes-mp3-tags-without-their-artwork-after-the-element-plays)'s
stage 1, so it sits just below it.

**Sources.** [Chromium's html_media_element.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/html/media/html_media_element.cc),
[HTML's media load algorithm](https://html.spec.whatwg.org/multipage/media.html#concept-media-load-algorithm),
[WebKit bug 259925](https://bugs.webkit.org/show_bug.cgi?id=259925).


### 50. Preload the three @imported stylesheets so CSS loads in one round trip

*Load · 0.75 h · gain 1 · score 5 · confidence high*

**Problem.** The page links only app.css (`public/index.html:13`), which
@imports tokens, layout and look (`public/css/app.css:8-10`). So the styles
take two round trips, and the deferred scripts wait for every stylesheet that
blocks scripts, imports included.

**Fix.** Before the app.css link, add `<link rel="preload"
href="/css/tokens.css" as="style">` and the same for layout.css and look.css,
with exactly the URLs that the imports resolve to. Cite the HTML spec on
deferred scripts and style sheets that block scripts. Check in three browsers'
waterfalls that each file is fetched once. A link can't take a layer, so the
@imports stay.

**Effort and gain.** Three lines and a comment. One loopback round trip today,
about a millisecond, and tens of ms once remote.

**Risks.** A URL that doesn't match fetches twice, and the browser warns about
an unused preload.

**Sources.** [CSS Wizardry on CSS and network performance](https://csswizardry.com/2018/11/css-and-network-performance/),
[HTML on styling and scripting](https://html.spec.whatwg.org/multipage/semantics.html#interactions-of-styling-and-scripting).


### 51. Show podcast:person pictures as the server's 64–128 px thumbnails

*Network · 1 h · gain 1 · score 5 · confidence high*

**Problem.** The People row draws each person's picture at 48 px from its
original URL, often a large photo (`elektrolyt/ui/details.cljc:67-75`), while
every other picture uses the server's thumbnails through `artwork/img`
(`elektrolyt/ui/artwork.cljc:77-91`).

**Fix.** Replace the bare img with `(artwork/img {:artwork img} 48 {:loading
"lazy"})`, as `elektrolyt/player/ui.cljc:27` does, keeping the `web-url?` guard
and the empty alt. The CSS keeps the pictures round whatever the thumbnail's
shape (`public/css/look.css:691-695`). Add a unit test of the srcset and the
sizes of 48px beside the People test with a picture
(`test/dk/simongray/elektrolyt/show/ui_test.cljc:175-194`), and look at the
People scenes.

**Effort and gain.** One line, a test and a look at the scenes. It saves bytes
and decoding in an aside that few listeners scroll to.

**Risks.** A picture narrower than 64 px is labelled 64w, which the 48 px box
absorbs. The person's image host now sees the server instead of the listener's
browser, a small gain in privacy.

**Sources.** [web.dev on responsive images](https://web.dev/articles/serve-responsive-images),
[HTML on sizes](https://html.spec.whatwg.org/multipage/images.html#sizes-attributes).


### 52. Skip identical renders, guard the title, and read the theme colour after paint

*Rendering · 1 h · gain 1 · score 5 · confidence medium*

**Problem.** ClojureScript's atoms notify their watches even when a swap returns
the identical state (`cljs/core.cljs:4660-4673` in ClojureScript 1.12.145), so a
swap that changes nothing renders the whole page (`elektrolyt.cljs:480`).
Several happen per interaction: `answered!` after every Play
(`elektrolyt/player/audio.cljs:243`), and `press!` or `released!` at every skip
and every release of the seek bar (`audio.cljs:174, 411`). The `render!`
function also sets document.title each time
(`elektrolyt/ui/document.cljs:101`), and reads getComputedStyle for the theme
colour right after Replicant's DOM writes (`document.cljs:44-52, 104`). That
forces a style recalc, and possibly a layout, given the CSS's viewport media
queries. It's wasted only when another render follows in the same task, e.g.
the five of a Play click.

**Fix.** Have the `::render` watch skip `(identical? old new)`, set
document.title only when it differs, and run `theme-colored!` once after the
paint through runtime (a flag and `(.then (runtime/painted!) …)`, or #12's
scheduler) instead of tracking its inputs, which include CSS state that the
store doesn't hold. Keep the call at transitionend (`elektrolyt.cljs:471-472`).
Add a unit test that an identical swap doesn't render, and a check of the meta
theme-color in the shell e2e spec after a change of theme and of the playing
artwork. Check that the reload after a hot reload in dev still renders.

**Effort and gain.** Two guards and one deferred call, a unit test and an e2e
assertion. One whole render less per Play or skip, which is barely noticeable
alone and mostly covered by #12.

**Risks.** The theme-color meta updates a frame later, which nobody can see.

**Sources.** [Paul Irish on what forces layout](https://gist.github.com/paulirish/5d52fb081b3570c81e3a),
[HTML on document.title](https://html.spec.whatwg.org/multipage/dom.html#document.title),
[HTML on meta theme-color](https://html.spec.whatwg.org/multipage/semantics.html#meta-theme-color),
[web.dev on layout thrashing](https://web.dev/articles/avoid-large-complex-layouts-and-layout-thrashing).


### 53. Preload /api/server from index.html so it arrives while the scripts evaluate

*Startup · 1 h · gain 1 · score 5 · confidence low*

**Problem.** The first render waits for /api/server as well as the library, and
its request goes out only after both deferred scripts have run init
(`elektrolyt.cljs:434-445, 475-482`). With a server reached over a network, that
adds a round trip whenever the library is read first, e.g. for a new listener
or a small library.

**Fix.** Add `<link rel="preload" href="/api/server" as="fetch"
type="application/transit+json" crossorigin>` to public/index.html before the
scripts, with a comment that cites HTML's preload key and MDN's note that a
fetch preload needs crossorigin to match a default fetch(). The
`load-server-info!` effect stays as it is. Add an e2e test in Chrome and
Firefox that counts one request to /api/server and no warning about an unused
preload, and check Safari by hand.

**Effort and gain.** One line with a comment and a small test. At most one
round trip, only when IndexedDB finishes first, and nothing on localhost today.

**Risks.** If the fetch's credentials mode changes, the browser fetches twice
and warns.

**Sources.** [MDN on preload](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Attributes/rel/preload),
[HTML on preload](https://html.spec.whatwg.org/multipage/links.html#link-type-preload),
[Fetch's request mode](https://fetch.spec.whatwg.org/#concept-request-mode).


### 54. Hash each served file once per version, not on every request

*Load · 1.5 h · gain 1 · score 4 · confidence high*

**Problem.** Every file the server sends carries an ETag that it computes by
reading the whole file and hashing it with SHA-256
(`elektrolyt/server.clj:135-157`). It does so for every request, including the
conditional ones that end in a 304, since the tagging runs before the 304 check
(`server.clj:159-172`): about 16 MB per dev page load, and 13 MB more when the
worker starts. The hash has to finish before the headers go out.

**Fix.** Keep the tags in an atom keyed by the file's canonical path, its
lastModified in ms and its length, and hash only on a miss. Keep the comment on
RFC 9110 that says why Last-Modified isn't enough. Add a JVM test that a
rewritten file gets a new tag and an unchanged one keeps it. An ETag of mtime
and length is a stateless alternative, with the same case of collision.

**Effort and gain.** A small memo with a JVM test. Roughly 10–30 ms of server
work per dev load, part of it delaying a response, and almost nothing after #8.

**Risks.** A rewrite within the same millisecond to the same length keeps a
stale tag, which is very unlikely.

**Sources.** [RFC 9110, section 8.8.3](https://www.rfc-editor.org/rfc/rfc9110#section-8.8.3),
[RFC 9111, section 5.2.2.4](https://www.rfc-editor.org/rfc/rfc9111#section-5.2.2.4).


### 55. Tally timeupdates, store changes and renders per minute while hidden, in dev

*Measurement · 1.5 h · gain 1 · score 4 · confidence medium*

**Problem.** Nobody has traced the hidden state in a real browser: how often iOS
Safari runs the page's JavaScript with the screen locked, and how many renders a
minute happen there and in Chrome on Android. Items #3 and #44 rest on engine
source.

**Fix.** In dev only (goog.DEBUG), count timeupdate events
(`elektrolyt/runtime.cljs:223`), store changes, renders with their total ms
(`elektrolyt.cljs:479`) and posts on the player's channel while the page is
hidden. At visibilitychange, log them with Telemere as `:perf/hidden-tally`,
with rates per minute and the hidden time (`runtime.cljs:119-134`). Run it with
the screen locked in Chrome for Android, through remote debugging with adb
reverse, and in desktop Firefox and Chrome, before and after #3 and #44. The
iPhone's run waits until the dev build can be served where an iPhone reaches
it, since the server answers only to localhost. It could be part of #82.

**Effort and gain.** A few counters and a log line, with nothing in a release
build. Measurement only.

**Risks.** Remote inspectors can keep a page awake themselves.

**Sources.** [MDN on visibilityState](https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilityState),
[WebKit bug 279055](https://bugs.webkit.org/show_bug.cgi?id=279055),
[Chrome on timer throttling](https://developer.chrome.com/blog/timer-throttling-in-chrome-88),
[Chrome on remote debugging a local server](https://developer.chrome.com/docs/devtools/remote-debugging/local-server).


### 56. Start the web worker at idle after the first paint

*Startup · 1.5 h · gain 1 · score 4 · confidence low*

**Problem.** The worker is made at the first request that needs it
(`elektrolyt/worker/fetching.cljs:250-265`). When the first page isn't an
outside show's and no feed is due at start, that request waits for worker.js to
revalidate, load and evaluate, which in release includes shared.js once more.
That request is the whole feed of the session's first new show, or a
subscription to a show whose whole feed came from the cache. A Show page's
first episodes don't wait, since the page fetches them itself
(`elektrolyt/show/feeds.cljs:145-156`).

**Fix.** In init's last `.finally`, add `(.then (runtime/painted!)
#(runtime/idle! 3000 fetching/web-worker!))`, or a `fetching/warm!` with a
research comment. The `web-worker!` function makes one client and starts again
after a failure (`fetching.cljs:235-242`). Update STATE.md's row for the web
worker, and add a unit test that `warm!` makes one client that `ask!` reuses, an
e2e test that waits for page.waitForEvent('worker') after Home loads, and a
check that show.spec.mjs's restart test still counts two loads.

**Effort and gain.** One call, a STATE.md edit and two tests. It helps only the
first worker request of a session that neither the first page nor a refresh
started: plausibly 100–300 ms in release, unmeasured.

**Risks.** Every tab holds a worker heap, perhaps 5–20 MB in release, even when
it's unused, and on a phone that can make the browser discard background tabs
sooner. In Safari `idle!` fires right after the current task, which #15
changes.

**Sources.** [MDN on requestIdleCallback](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestIdleCallback),
[caniuse on requestIdleCallback](https://caniuse.com/requestidlecallback),
[HTML on dedicated workers](https://html.spec.whatwg.org/multipage/workers.html#dedicated-workers-and-the-worker-interface),
[shadow-cljs on web workers](https://shadow-cljs.github.io/docs/UsersGuide.html#_web_workers),
[web.dev on off-main-thread work](https://web.dev/articles/off-main-thread).


### 57. Run the e2e tests with reduced motion and check that all motion stays gated

*Measurement · 2 h · gain 1 · score 4 · confidence high*

**Problem.** The motion items below add transitions, keyframes and smooth
scrolling, and several e2e tests measure boxes right after a click or a focus
(`test/e2e/settings.spec.mjs:52-79`, `test/e2e/search.spec.mjs:98-109`,
`test/e2e/shell.spec.mjs:5-25`), so they'd flake. Nothing checks that a new rule
stays behind prefers-reduced-motion. Today all motion is in one no-preference
block (`public/css/look.css:1019-1044`), with transitions of zero duration
outside it (`public/css/layout.css:997, 1021, 1027, 1031`), and the colour tests
already poll through the seed transition.

**Fix.** Set `use.reducedMotion: 'reduce'` in playwright.config.mjs, which
Playwright 1.64 supports. Each motion feature gets its own test under
no-preference that waits for its animations or transitionend before it
measures. In look_test.cljs, add a small scanner of at-rules by brace depth,
with tests of its own, that checks every transition, animation, keyframes use
and @starting-style with a duration above zero lies inside a no-preference
block, and allows zero durations and none. The existing regexes can't tell
nesting (`test/dk/simongray/elektrolyt/look_test.cljs:26-35`). Gate smooth
scrolling and view transitions in CSS, not JS. Leave out a `runtime/motion?`
until a feature calls `.animate()`.

**Effort and gain.** A config line and a scanner with tests, plus a comment that
cites C39 and CSSOM View. Nothing a listener sees, and worth building with the
first motion item.

**Risks.** The suite stops covering the default, animated experience.

**Sources.** [Playwright's reducedMotion](https://playwright.dev/docs/api/class-testoptions#test-options-reduced-motion),
[WCAG technique C39](https://www.w3.org/WAI/WCAG22/Techniques/css/C39),
[MDN on prefers-reduced-motion](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion),
[CSSOM View on scroll-behavior](https://drafts.csswg.org/cssom-view/#propdef-scroll-behavior),
[MDN on scroll-behavior](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-behavior).


### 58. Pulse the bookmark while a subscription works, and stop within 5 s

*Motion · 3 h · gain 2 · score 8 · confidence medium*

**Problem.** Pressing Subscribe swaps the thin outline for the filled bookmark,
in grey since it's aria-disabled (`public/css/look.css:436-441, 460-462`), and
nothing moves until it's done. Subscribing and waiting share the class and the
attribute, so CSS can't tell them apart (`elektrolyt/ui/subscription.cljc:79-88`).
The wait covers fetching the whole feed when the page doesn't have it, and the
worker's documents; since the subscribe fix, the main thread is mostly free
meanwhile. A sighted listener sees a grey icon that might be broken, while
'Subscribing to …' is visually hidden text and a tooltip.

**Fix.** Add a class for the subscribing state, e.g. `:class ["bookmark"
"subscribing"]`; the icon button passes attributes through
(`elektrolyt/ui/controls.cljc:124-131`). In look.css's no-preference block, give
`.icon-button.bookmark.subscribing .icon` a short drop (translateY from about
−10% over 200 ms) and a pulse of opacity, from 1 to about 0.5 and back over
about 1.2 s, that starts after 400 ms. Run it three times, so that it stops
before 5 s (WCAG 2.2.2). Animate only the svg, so the focus ring never moves.
Reduced motion keeps the still grey fill. Add a Portfolio scene, an e2e test
under no-preference that the class and an animation are there while it
subscribes and gone after, and a profile in Firefox and Chrome that the pulse
keeps moving through any long task that's left.

**Effort and gain.** One class and two keyframes with a comment, while the
scene, the test and the profiles take most of the time. It shows progress
during the fetch and the worker's documents, mostly under a second, on an
occasional path. If a freeze of several seconds remains in big libraries, a
pulse on the compositor matters more.

**Risks.** Whether Firefox and Safari run an outer svg's opacity and transform
animation off the main thread is unverified; a span around the icon is the
fallback.

**Depends on.** [#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated),
so it sits just below it.

**Sources.** [Understanding WCAG 2.2.2](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html),
[web.dev on animations](https://web.dev/articles/animations-overview),
[dbaron on compositor animations](https://dbaron.org/log/20150916-compositor-animations),
[Mozilla bug 1218620](https://bugzilla.mozilla.org/show_bug.cgi?id=1218620),
[Understanding WCAG 2.3.3](https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html).


### 59. Glide queue, inbox and Library rows to their new places with a FLIP hook

*Motion · 7 h · gain 2 · score 6 · confidence medium*

**Problem.** Move up and down, Play next, Remove, Dismiss and Undo reorder or
remove keyed rows in one frame (`elektrolyt/library/queue/ui.cljc:121-158`,
`elektrolyt/library/inbox/ui.cljc:126-138`, `elektrolyt/ui/home.cljc:34-36`),
and a refresh can re-sort the Library's shows
(`elektrolyt/library/ui.cljc:48, 76-80`). The focus follows the moved row or its
neighbour (`elektrolyt/ui/rows.cljc:42-58`), so a keyboard user keeps their
place, but a sighted pointer user sees rows jump and has to find where things
went.

**Fix.** Add `:replicant/on-render [[:list/glide]]` to ol.queue, ul.inbox,
ul.library-shows and Home's ol.queue; Replicant's data hooks get the node and a
memory (`replicant/core.cljc:330-371`). The effect works in four steps:

1. When the children are the same nodes in the same order as last time, it
   records their positions and returns, since playback renders often and would
   otherwise force a layout read each time.
2. It takes positions relative to the list's own box, so that the Undo button
   appearing above the list doesn't glide every row.
3. For clicks, an effect dispatched before the change takes the first
   positions, e.g. `[[:list/measure "ol.queue"] [:queue/move …]]`, with
   getBoundingClientRect, so that a row still gliding starts where it shows.
   The positions from the last render serve only for changes nobody clicked,
   e.g. a refresh's re-sort, and not when the list's width has changed.
4. It animates the moved children with `el.animate` from a translate to none
   over 220 ms, only when motion is allowed, only for rows in view, and not at
   all when more than a few dozen move.

This is the first `.animate()`, so add `runtime/motion?` here. Unit-test the
pure delta function in Node, and add an e2e test under no-preference that a
moved row animates, ends in place and keeps the focus.

**Effort and gain.** The effect with the measure-first path, the rules for
skipping and resizing, hooks on four lists, a delta test, e2e tests in two
browsers and a look in Safari. Triage gets easier to follow for pointer users,
though removed rows still vanish at once. Polish, unmeasured.

**Risks.** A future `:replicant/mounting` anywhere would defer the hook two
frames and show the jump before the glide. Rows sliding under a pointer for
220 ms could take a stray click.

**Depends on.** [#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated).

**Sources.** [Aerotwist on FLIP](https://aerotwist.com/blog/flip-your-animations/),
[MDN on animate()](https://developer.mozilla.org/en-US/docs/Web/API/Element/animate),
[MDN on getBoundingClientRect](https://developer.mozilla.org/en-US/docs/Web/API/Element/getBoundingClientRect),
[Replicant's life-cycle hooks](https://replicant.fun/life-cycle-hooks/),
[Understanding WCAG 2.3.3](https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html).


### 60. Ease the Clear the queue dialog and its backdrop in, and leave its exit instant

*Motion · 1 h · gain 1 · score 5 · confidence high*

**Problem.** 'Clear the queue?' appears in one frame, and the browser's backdrop
darkens at once, since the dialog has no CSS at all
(`elektrolyt/library/queue/ui.cljc:94-105`). The exit is the hard half. Firefox
and Safari lack the overlay property, so a closing dialog leaves the top layer
at once, and Safari fades it out at the top of the page under other content. In
Chrome, Clear empties the queue at the close event (`elektrolyt.cljs:316-325`),
so a fading dialog would read 'This takes 0 episodes out of the queue.'

**Fix.** Inside look.css's no-preference block, add `dialog[open] { transition:
opacity 0.2s, translate 0.2s }` with `@starting-style { dialog[open] {
opacity: 0; translate: 0 0.5rem } }`, and `dialog[open]::backdrop { transition:
opacity 0.2s }` with an @starting-style of its own, since it can't nest for the
pseudo-element. An entry from display: none needs no transition of display or
overlay. Leave the exit instant everywhere: a fade out would need @supports
(overlay: auto), which only Chrome passes, and a text that doesn't change while
the dialog closes. The existing e2e test (`test/e2e/queue.spec.mjs:60-80`)
covers closing and focus. Cite @starting-style and the WebKit and Mozilla bugs
on overlay.

**Effort and gain.** Two rules and two @starting-style blocks, a comment and a
look in three browsers with motion on and reduced. Polish on a rare path.

**Risks.** Playwright waits out the 0.2 s translate before it presses Cancel,
which #57 removes.

**Depends on.** [#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated).

**Sources.** [MDN on @starting-style](https://developer.mozilla.org/en-US/docs/Web/CSS/@starting-style),
[Chrome on entry and exit animations](https://developer.chrome.com/blog/entry-exit-animations),
[MDN on transition-behavior](https://developer.mozilla.org/en-US/docs/Web/CSS/transition-behavior),
[MDN's compatibility data for overlay](https://raw.githubusercontent.com/mdn/browser-compat-data/main/css/properties/overlay.json),
[WebKit bug 275184](https://bugs.webkit.org/show_bug.cgi?id=275184),
[Mozilla bug 1841456](https://bugzilla.mozilla.org/show_bug.cgi?id=1841456),
[the APG's modal dialog](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/).


### 61. Scroll smoothly to a directory's list from Search's jump row

*Motion · 1.5 h · gain 1 · score 4 · confidence medium*

**Problem.** Search's jump row moves the sideways row of directory lists, or
main on a phone, to the chosen list in one frame
(`elektrolyt/ui/document.cljs:136-149`, whose only use is
`elektrolyt/search/ui.cljc:241`), so the listener can't see which way it went.

**Fix.** In `show!`, pass `:behavior (if (runtime/motion?) "smooth" "auto")`;
auto is instant here, since nothing sets scroll-behavior. Keep `scroll-to!`,
the heading's focus and the focus call instant, and don't set scroll-behavior on
main, which would smooth the scrolls of focus and fragments too. Make
`test/e2e/search.spec.mjs:86-110` poll the box until it settles, or run it under
#57's reduced motion, and add an e2e test under no-preference that the list ends
in place with its heading focused.

**Effort and gain.** One option, a comment and two tests. Polish on an
occasional path, mostly on wide screens.

**Risks.** Chromium runs nested smooth scrolls one after the other, the outer
first, so a jump that scrolls both main and the row takes two animations.

**Depends on.** [#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated).

**Sources.** [MDN on scrollIntoView](https://developer.mozilla.org/en-US/docs/Web/API/Element/scrollIntoView),
[CSSOM View on scrollIntoView](https://drafts.csswg.org/cssom-view/#dom-element-scrollintoview),
[Chromium's code review 2650343008](https://codereview.chromium.org/2650343008),
[Understanding WCAG 2.3.3](https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html).


### 62. Show 'Show the new episodes' in the status line's place, so the list stays put

*Motion · 2 h · gain 1 · score 4 · confidence medium*

**Problem.** A show outside the library whose cached feed is past its time gets
fetched again. If that fetch has new episodes, 'Show the N new episodes'
appears as a 36 px line of its own between the status paragraph and the list
(`elektrolyt/show/ui.cljc:194, 203-209`; `public/css/tokens.css:115`), and the
list moves down by itself, usually soon after the page opens. That's the shift
without input that `show/refetched` holds the episodes back to avoid
(`elektrolyt/show.cljc:328-342`), and a tap can land on the wrong row.

**Fix.** Put the button where the status line's text was. Keep the role=status
paragraph, make its whole text visually hidden while the new episodes wait, and
show the button on that line. The button is 36 px tall, and the status line
about 25 px plus an 8 px margin, so the list barely moves. A row of the count
and the button doesn't fit at 320–375 px and would wrap anyway, so if you want
the count visible, use the row on wide screens only. Update the assertion on
visible text in `test/e2e/cache.spec.mjs:106-123`, add an e2e test that the
first episode keeps its y when the new episodes arrive, at 320 and 900 px, and
update the scene (`dev/dk/simongray/elektrolyt/show/ui_scenes.cljs:87`).

**Effort and gain.** A conditional class, a few lines of layout, two e2e
changes and the scene. An occasional event: a visit past the cache's age to an
outside show with new episodes.

**Risks.** Hiding the count while the button shows is your call. Screen readers
still hear the whole status.

**Sources.** [web.dev on CLS](https://web.dev/articles/cls) and
[on shifts after input](https://web.dev/articles/cls#user-initiated_layout_shifts),
[MDN on LayoutShift](https://developer.mozilla.org/en-US/docs/Web/API/LayoutShift),
[Understanding WCAG status messages](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html).


### 63. Start the first refresh after the first paint, at idle time

*Startup · 2 h · gain 1 · score 4 · confidence medium*

**Problem.** Init calls `refresh-if-due!` in the same task as the router's first
render (`elektrolyt.cljs:479-482`). When feeds are due, `refresh!` swaps the
refresh's status at once (`elektrolyt/library/refresh.cljs:163`), which is a
second full render before any paint. The due computation and the worker's start
wait for the IndexedDB read of `caught-up!`, so they likely run after the paint
already. The worker's answer is then applied in one long change whenever it
arrives (`refresh.cljs:120-127`).

**Fix.** Replace the direct call with `(.then (runtime/painted!)
#(runtime/idle! 2000 (fn [] (refresh/refresh-if-due! store))))`. Optionally,
run `refreshed!` from `runtime/idle!` with a 1 s timeout, keeping the schedule
after it. Until #7 lands, that apply is the long task, and idle scheduling only
keeps it from starting while input waits. Add a unit test with the runtime
stubbed, and check library.spec.mjs's expectations at startup.

**Effort and gain.** A few lines, a comment and updated tests. The first paint
stops waiting for one render, 12–14 ms for a Show page in dev. The idle apply
doesn't shorten the long task, and Safari has no requestIdleCallback.

**Risks.** New episodes appear up to 2 s later.

**Sources.** [MDN on requestIdleCallback](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestIdleCallback),
[caniuse on requestIdleCallback](https://caniuse.com/requestidlecallback),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[HTML's event loop](https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model).


### 64. Report what's in the release bundles, and fail CI over a size budget

*Measurement · 2.5 h · gain 1 · score 4 · confidence high*

**Problem.** Nobody knows which namespaces dominate a release build, and growth
from a library's bump goes unnoticed. The last measurement, at 0199cf7 before
Telemere added about 23 kB gz, was 485.4 kB gz for the page and 356.5 kB for
shared.js and worker.js together. The server doesn't compress responses
(`elektrolyt/server.clj:278-295`, and Pedestal 0.8.2 has no gzip), so the
browser downloads, parses and compiles the raw minified size, several times the
gzipped one.

**Fix.** Add a dev function that calls
`(shadow.cljs.build-report/generate (shadow.cljs.devtools.api/get-build-config
:app) {:report-file "target/build-report.html" :data-file
"target/build-report.edn" :print-table true})`, reads each module's raw and
gzipped sizes from the EDN, and compares them with a budgets file at today's
sizes plus 5%. The report builds into a release snapshot of its own, which
leaves public/js alone, and needs `:report-file`
(`shadow/cljs/build_report.clj:113-147, 258-315` in shadow-cljs 3.5.7). Locally,
run it in the running JVM's nREPL, never a fresh shadow CLI beside it. In CI, a
step 'Release build and sizes' runs it and uploads the report beside the e2e
traces. Budget the raw size as well as the gzipped one.

**Effort and gain.** One function with a budgets file, a README paragraph and a
CI step. Measurement only: it decides whether lazy loading, trimming or
compression are worth doing, and keeps their gains.

**Risks.** CI grows by an :advanced build of a minute or more, and budgets need
updating when a dependency is added on purpose.

**Sources.** [shadow-cljs's build report](https://shadow-cljs.github.io/docs/UsersGuide.html#build-report),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[web.dev on code splitting](https://web.dev/articles/reduce-javascript-payloads-with-code-splitting).


### 65. Measure proxy and thumbnail waits with Server-Timing before tuning them

*Measurement · 2.5 h · gain 1 · score 4 · confidence high*

**Problem.** Nothing shows how long a request waits at the server. The proxy's
four slots and the thumbnails' two jobs queue silently
(`elektrolyt/server.clj:182-195, 240-261`;
`elektrolyt/server/artwork.clj:53-54, 260-280, 352-397`), so a slow Show page
or a grey box of artwork can't be put down to the network, the server's queue
or the page.

**Fix.** In `at-most`, time the wait for a turn and the handler's call, and add
`Server-Timing: queue;dur=…, upstream;dur=…` to the response, the 503 included.
The app uses wary-fetch's buffered handler, so the upstream time is known before
the headers go out. In the artwork handler, record the wait for a job or for
another request's renewal and the time to fetch and scale, as job and scale, or
cache;dur=0 for a hit, and keep them out of the stored entry. Cite the W3C spec,
and check the header in server_test.clj and artwork_test.clj. The timings then
show in DevTools and in PerformanceResourceTiming.serverTiming, in the page and
the worker alike.

**Effort and gain.** A few lines in `at-most` with a test, and the artwork's
timing threaded through its shared and stale paths. Measurement for #18, #32
and #47.

**Risks.** It reveals internal timings, but the server listens on localhost.
Keep the header consistent on 304s.

**Sources.** [MDN on Server-Timing](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Server-Timing),
[MDN on serverTiming](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceResourceTiming/serverTiming),
[MDN's compatibility data for Resource Timing](https://github.com/mdn/browser-compat-data/blob/main/api/PerformanceResourceTiming.json),
[the W3C's Server Timing](https://www.w3.org/TR/server-timing/).


### 66. Ease the caret, the switch's knob, the volume panel and the artwork hover

*Motion · 2.5 h · gain 1 · score 4 · confidence high*

**Problem.** Four small changes of state snap in a frame while the buttons
around them fade: a folded section's caret (`public/css/look.css:419-434`), a
switch's knob in Settings (`look.css:486-501`), the volume panel
(`public/css/layout.css:986-1032`) and hovered artwork (`look.css:594-596`).
Each is barely noticeable.

**Fix.** In look.css's no-preference block:

1. For the caret, write both states with the rotate and translate properties
   instead of vertical-align, and give `button.disclosure::after` a transition
   of its own, since the button's doesn't reach it.
2. For the switch, register `@property --knob { syntax: "<length-percentage>";
   inherits: false; initial-value: 0px }`. The initial value must be
   computationally independent, so it can't be 0.62rem. Set it to 0.62rem, and
   to calc(100% − 0.62rem) when checked, draw the knob at var(--knob) in both
   gradients, the forced-colours ones too (`look.css:1004-1015`) or let those
   jump, and transition it with background-color.
3. For the volume panel, a transition shorthand in the look layer would beat
   layout's transition-delay: 0s on the shown states, since look comes after
   layout (`public/css/app.css:6-10`), and show the panel 0.3 s late. Keep its
   visibility and opacity in one layer, restating the selectors for shown,
   hovered and dismissed, or put the opacity in layout beside the visibility
   under a reduced-motion query of its own. It must fade in at once, fade out
   within the 0.3 s grace and hide at once on Escape.
4. For the artwork, add `.artwork-link img { transition: filter 0.15s }`.

Check each in Portfolio in light, dark and forced colours, and keep the volume's
e2e test green.

**Effort and gain.** CSS in four places and a comment, the volume's work across
layers and the checks. Polish.

**Risks and principles.** The volume panel's trap in the cascade layers: done
naively, it shows late, which would be a real regression. Its visibility is
layout and its fade is look, so keep the two in step across the layers.

**Depends on.** [#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated).

**Sources.** [MDN on @property](https://developer.mozilla.org/en-US/docs/Web/CSS/@property),
[MDN on @layer](https://developer.mozilla.org/en-US/docs/Web/CSS/@layer),
[MDN on rotate](https://developer.mozilla.org/en-US/docs/Web/CSS/rotate),
[web.dev on animations](https://web.dev/articles/animations-overview).


### 67. Cache the worker's encoded feed as it is, without encoding it again

*Main thread · 2.5 h · gain 1 · score 4 · confidence medium*

**Problem.** The worker encodes a whole feed in like-minded's Transit codec
(`elektrolyt/worker/fetching.cljs:165-172`), the page decodes it
(`fetching.cljs:229`), and later the page encodes the same feed again in the
same codec to cache it (`elektrolyt/show/feeds.cljs:41-44`;
`elektrolyt/runtime/cache.cljs:18-26, 80-84`). Chrome and Firefox do that at
idle, Safari right after the feed lands, just as the listener starts to scroll.
A cached show whose check gets a 304 also encodes its whole feed again to renew
its time (`feeds.cljs:186-189`). The encode is estimated at 10–25 ms for
1–2 MB, more for 20 MB.

**Fix.** Keep the page as the cache's writer, and skip the encode. The worker's
client resolves with the decoded answer carrying its string in metadata, e.g.
`(vary-meta (decode encoded) assoc ::encoded encoded)`, and the worker encodes
the answer without its body and report, which the page never reads. The
`keep-show!` effect writes that string through a copy of `app-cache/shows` whose
codec doesn't encode. In like-minded, `put!` encodes with the cache's own codec
(`like_minded/cache.cljc:132-146`), so it writes the string as the body, and
`fetch!` decodes as before. The renewal on a 304 keeps encoding at idle, since
it needs new validators in the body. Keep the check's rule that an emptied feed
doesn't replace the cache (`feeds.cljs:177-181`). Add a unit test that a value
cached from the string reads back as `show/kept` does; cache.spec.mjs keeps
passing.

**Effort and gain.** A metadata key, a cache config that doesn't encode,
`keep-show!` and a test. One encode less per outside Show page, most visible in
Safari with large feeds. Estimated.

**Risks.** The page holds the string until the idle write. The cache's codec and
the library's must stay the same, which a test should assert.

**Sources.** [caniuse on requestIdleCallback](https://caniuse.com/requestidlecallback),
[MDN on IndexedDB in workers](https://developer.mozilla.org/en-US/docs/Web/API/WorkerGlobalScope/indexedDB),
[Surma, When should you be using workers?](https://surma.dev/things/when-workers/).


### 68. Write only the icons the code uses, if listening stays on the dev build

*Load · 2.5 h · gain 1 · score 4 · confidence medium*

**Problem.** In the dev build you listen on, ui.icons is one escaped evalLoad
string of 2,143,483 B inside main.js, holding 4,536 defs
(`elektrolyt/ui/icons.cljc`), though src uses 28 icons. In Node 26's V8 on your
Mac, it takes about 7–8 ms to parse the string, which Chrome can stream off the
main thread, and about 15.5 ms of cold eval on it, so roughly 15–25 ms per full
load. A release build already drops the unused icons.

**Fix.** In `dev/dk/simongray/elektrolyt/phosphor.clj`, have `write-icons!`
write only the names that src/, dev/ and test/ reference as icons/<name>. The
scan must include test/, where icons/info and icons/rewind appear alone. Keep
the weights as arguments. Write the icons again, and update the generated
docstring and README's paragraph on icons: adding an icon means referencing it
and running `write-icons!` again. A missing icon already fails CI through
clj-kondo's unresolved-var, so add a JVM test beside runtime_test.clj for stale
ones.

**Effort and gain.** A scan of the sources, a fresh write, a JVM test and two
edits of text. About 15–25 ms per full load of the dev build, which listeners
can't notice, and moot after #8.

**Risks and principles.** Picking a new icon takes a step at the REPL. It
changes the convenience of having every icon at hand, and keeps one def per
icon.

**Sources.** [V8, The cost of JavaScript in 2019](https://v8.dev/blog/cost-of-javascript-2019),
[clj-kondo's linters](https://github.com/clj-kondo/clj-kondo/blob/master/doc/linters.md),
[clj-kondo's README](https://github.com/clj-kondo/clj-kondo/blob/master/README.md),
[the shadow-cljs guide](https://shadow-cljs.github.io/docs/UsersGuide.html).


### 69. Give the worker the library's options without the page's library namespace

*Startup · 2.5 h · gain 1 · score 4 · confidence medium*

**Problem.** The worker's fetching namespace requires the page's library
namespace only for send, store, config and options
(`elektrolyt/worker/fetching.cljs:17-23`). That pulls the page's runtime into
the worker's graph: reitit.frontend and its history, play-nice's page,
session, media and controls namespaces, and the app's operations, queue, inbox,
player and more (`elektrolyt/library.cljs:15-23`, `elektrolyt/runtime.cljs:4-9`).
The build then counts them as shared (`public/js/manifest.edn`), so every
start of the worker evaluates their top-level code where there's no window or
history.

**Fix.** Add a namespace, e.g. library.options (.cljs), that holds send, store,
config, options and document-name with their docstrings, and requires only
wary-fetch's proxy and like-minded's IndexedDB and Transit. Point the 21 call
sites at it, with no aliases left in library.cljs. Add a JVM test beside
runtime_test.clj that reads the ns forms reachable from the worker's namespace,
and fails if runtime, reitit or play-nice's page namespaces are among them.
Then check the manifest and compare the worker's gzipped release size.

**Effort and gain.** A move of five defs, the call sites, a structural test and
a check of sizes. In release, Closure already moves functions that only the
page calls into main.js, so the worker mostly loses top-level statements: a few
kB, and well under a millisecond per start. The worker also stops depending on
the page's runtime by accident.

**Risks.** None found, since a mistake only moves code between shared.js and
main.js.

**Sources.** [shadow-cljs on web workers](https://shadow-cljs.github.io/docs/UsersGuide.html#_web_workers),
[Closure's CompilationLevel](https://github.com/google/closure-compiler/blob/master/src/com/google/javascript/jscomp/CompilationLevel.java),
[HTML on importScripts](https://html.spec.whatwg.org/multipage/workers.html#dom-workerglobalscope-importscripts).


### 70. Cap the episodes a feed retains after its host drops them, with :max-items

*Load · 2.5 h · gain 1 · score 4 · confidence low*

**Problem.** A subscribed feed keeps every episode that a later fetch no longer
returns (`podcast/app/feeds/impl.cljc:59-78`). So a show whose host serves only
the latest 50–300 items grows by every episode it ever had, about 365 a year for
a daily show, and each stays in memory, in the feed's document and in the
index. A `:max-items` setting in podcast-clj caps this, dropping only retained
items that the profile holds nothing for (`podcast/app/library/impl.cljc:256-303`),
but the app's config doesn't set it (`elektrolyt/library.cljs:41-43`).

**Fix.** Add `:max-items` to the library's config, e.g. 1000, high enough to
bind only on windowed feeds that have retained a lot. The Show page still lists
every episode the feed serves today. Add a comment that cites RFC 5005 and
`capped`, a unit test of a library that retains more than the cap with one
played retained item that stays, and a line in STATE.md's row for `:library`.

**Effort and gain.** A line of config, a test and your choice of number. It
bounds months of growth for windowed shows. Nothing is measured.

**Risks and principles.** It's a product decision: episodes that a publisher
removed long ago disappear beyond the cap unless the listener touched them,
while Apple Podcasts and Pocket Casts keep everything they've seen. That's some
tension with keeping the listener's data.

**Sources.** [RFC 5005](https://www.rfc-editor.org/rfc/rfc5005).

### 71. Mark each stage from a Play click to the element's 'playing' event

*Measurement · 3 h · gain 1 · score 4 · confidence high*

**Problem.** Nobody has timed a Play up to the 'playing' event, in any browser
or on a phone, for an episode in the library, an outside episode, autoplay or
Next on the lock screen (`elektrolyt/player/audio.cljs:165-177, 359-384`).
Without it, #29, #48, #49 and #98 rest on curl and on reading the code.

**Fix.** In player.audio, mark the entry to `play!`, `ended!` and `press!`, the
moment after `media/source!`, and the first loadstart, loadedmetadata, canplay
and playing after them, recognized from the media state in `changed!`
(`audio.cljs:226-237`), which every media event reaches
(`elektrolyt/runtime.cljs:221-232`). Log one Telemere event, `:player/started`,
at debug, with the measures, the case, the visibility state and the
transferSize of the /proxy reads. In e2e, assert the order of the marks and a
generous budget, e.g. 'playing' within 3 s of the click on the fixture. Read the
logs on an Android phone through chrome://inspect with adb reverse; an iPhone
can't reach the server on localhost yet.

**Effort and gain.** Marks and a log event, a test of their order and a look on
a phone. It tells which of the Play items matter on your devices.

**Risks.** Cross-origin media requests hide their connection timings without
Timing-Allow-Origin, so the redirect chain shows only as a whole. A release
build drops debug logs, so the phone run uses dev.

**Sources.** [MDN on performance.mark](https://developer.mozilla.org/en-US/docs/Web/API/Performance/mark),
[Resource Timing](https://w3c.github.io/resource-timing/),
[Chrome on remote debugging a local server](https://developer.chrome.com/docs/devtools/remote-debugging/local-server).


### 72. Fade in what arrives after the page: more episodes, results, opened sections

*Motion · 3 h · gain 1 · score 4 · confidence medium*

**Problem.** Content that arrives in a page already on screen appears in one
frame: the rows of Show more, each directory's results, the linked shows, the
picks and an opened section's content (`elektrolyt/show/ui.cljc:210-219,
261-303`; `elektrolyt/search/ui.cljc:144-155, 190-206`;
`elektrolyt/library/picks/ui.cljc:40`; `elektrolyt/ui/controls.cljc:172-190`).
The player bar also appears at once the first time something plays.

**Fix.** In look.css's no-preference block, add `@starting-style { opacity: 0
}` with a 160 ms transition of opacity for .episodes > li, .search-results > li,
.linked-shows > li, .pick-list > li and the children of an opened section, and
fade #player in by opacity alone. Don't make it rise with a transform, which
would need body { overflow: clip } and make the shell's overflow at short
heights or large text unreachable (WCAG 1.4.10 and 1.4.4); a clip-path inset on
#player is the alternative, though it briefly clips the volume panel. Don't use
`:replicant/mounting`. Every li of a newly rendered page is new too, so lists
also fade at navigation, which is why you should pick this or #75, not both.
Add an e2e test under no-preference that Show more's rows end at opacity 1 with
the focus on the first new episode.

**Effort and gain.** CSS with a comment, checks of multiplied artwork and of the
player at short heights, an e2e test and a look in three browsers. Barely
noticeable.

**Risks.** During a fade an li is an isolated group, so its multiplied artwork
shows unblended and pops at the end. Fades on a new page could slow the LCP of
a list page's first load.

**Depends on.** [#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated).

**Sources.** [MDN on @starting-style](https://developer.mozilla.org/en-US/docs/Web/CSS/@starting-style),
[Chrome on entry and exit animations](https://developer.chrome.com/blog/entry-exit-animations),
[Compositing on isolation](https://www.w3.org/TR/compositing-1/#isolation),
[Understanding WCAG reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html),
[MDN on overflow](https://developer.mozilla.org/en-US/docs/Web/CSS/overflow).


### 73. Profile the 0.6 s seed transition, and narrow it if it drops frames

*Motion · 3 h · gain 1 · score 4 · confidence low*

**Problem.** Each change of the page's or the player's artwork transitions the
registered, inherited --seed and --bar-seed on .shell over 0.6 s
(`public/css/tokens.css:49-61`; `public/css/look.css:1023-1026`). Every token
made from them is computed at .shell and inherited (`look.css:128-152, 161-188,
284-290`), so each frame restyles the whole shell and repaints the tint, the
nav, the bar and the accent-coloured text on the main thread, alongside a show
navigation's own work, or during playback when a chapter image changes.
A benchmark on web.dev ran an inherited registered property at about 252 runs a
second against about 214,000 for one that isn't inherited. Whether it drops
frames is unmeasured.

**Fix.** Measure first, in Chrome at 4x and 6x CPU throttling, Firefox's
profiler and Safari's timeline, across navigations from show to show and
changes of chapter image, looking at style recalc and paint per frame. If frames
drop, a shorter fade, e.g. 0.3 s, halves them. Moving the transitions to #player
and the nav only works if the tokens made from the seeds are declared again
there: add #player to the rule for .player-next and .shell that declares the
bar's tokens, and declare --seed-accent again on the nav, since an unregistered
property is substituted where it's declared. Main's accent and the shell's tint
then snap. Move the theme-color hook (`elektrolyt.cljs:469-472`) to whichever
transition ends last.

**Effort and gain.** About an hour of profiling, then the CSS and checks in
light, dark and More contrast. Main scrolls on the compositor, so a slow fade
mostly stutters itself. Barely noticeable unless the profile shows long frames.

**Risks.** With split transitions, the tint, the links and the buttons snap
while the rail and the bar fade.

**Sources.** [web.dev on @property's performance](https://web.dev/blog/at-property-performance),
[MDN on registering properties](https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Properties_and_values_API/Registering_properties),
[CSS Variables on substitution](https://www.w3.org/TR/css-variables-1/#substitute-a-var),
[Chrome's performance reference](https://developer.chrome.com/docs/devtools/performance/reference).


### 74. Keep copies of the palette, bar seed, player height and theme-color

*Startup · 4 h · gain 1 · score 4 · confidence medium*

**Problem.** The static shell of #10 draws defaults: the 'pages' palette, no
player, and a theme-color of #353535 (`public/index.html:7-8`). The blocking
theme.js copies only the theme and the contrast (`public/theme.js:6-17`). So a
listener with another palette sees the nav recolour at the first render, one
with a queue sees the player appear at the foot
(`elektrolyt/player/audio.cljs:589-602`), and on Chrome for Android the
browser bar's colour jumps.

**Fix.** In `render!`, keep copies in localStorage, written only when they
change, as `attributed!` does: the palette's name, the bar seed's hex, the
player's measured height (Math.round of #player's offsetHeight, or nothing
without a player) and the last theme-color. The --player-height token covers
only the artwork's row (`public/css/layout.css:762-775`), so the height has to
be measured. The shell.js script reads them, sets data-palette and --bar-seed on the static
.shell and the meta theme-color, and adds an aria-hidden placeholder of that
height in the player's grid area. The first render replaces all of it with new
elements, so nothing transitions. Add STATE.md rows for the four keys and a line
under 'Facts in two places', unit tests, and an e2e test with a queued episode
and a delayed /api/server that main's height doesn't change at the first render,
in each palette.

**Effort and gain.** Four copies, a few lines of shell.js, STATE.md and two
tests. Polish at every start for listeners with a queue or another palette. The
player sits at the foot, so its arrival doesn't move the content at the top, and
what's noticed is mostly the change of colour.

**Risks and principles.** A stale copy shows a placeholder or a colour that the
first render changes, e.g. after another tab emptied the queue, and a height
measured at another width is wrong after a rotation. It adds four facts in two
places, each needing its reason in STATE.md.

**Depends on.** [#10](#10-paint-the-shells-frame-and-placeholders-from-indexhtml).

**Sources.** [HTML on meta theme-color](https://html.spec.whatwg.org/multipage/semantics.html#meta-theme-color),
[web.dev on CLS](https://web.dev/articles/cls),
[CSS Transitions on starting](https://drafts.csswg.org/css-transitions-1/#starting).


### 75. Fade each new page in, with main keyed by page and the announcer outside it

*Motion · 4 h · gain 1 · score 4 · confidence low*

**Problem.** A navigation swaps main's content in one frame, since Replicant
patches the one main#main (`elektrolyt/ui.cljc:215-240`). Browsers align the
focus scroll differently, so a page can open scrolled by main's top padding.
The jump itself is the web's norm, and a fade only softens it.

**Fix.** First move the announcer (`ui.cljc:196-201`) and the visually hidden
notes out of main into the shell, since a live region made anew at every
navigation would lose an announcement made in that render. Then key main by the
page, i.e. the route without its query, slug and fragment, as `same-page?` has
it (`elektrolyt/ui/routes.cljc:147-152`), so that Replicant makes a new element
(`replicant/core.cljc:886-897`). In look.css's no-preference block, add
`.shell > main { transition: opacity 180ms ease-out }` with `@starting-style {
opacity: 0 }`, but not at the first render, e.g. only once a data attribute on
the shell says the tab has navigated, so the LCP isn't delayed. A keyed main
always starts at scrollTop 0. Add e2e tests that an announcement survives a
navigation, the heading takes the focus, /settings#search lands, and the audio
keeps playing.

**Effort and gain.** The key and the CSS are small, while moving the announcer,
leaving out the first render and the checks take the rest. Pure softening, and
a 180 ms fade can make navigation feel slower as easily as smoother.

**Risks.** While main fades it's an isolated group, so the artwork multiplies
only with main's grain and pops back at the end, and the grain blinks out for a
moment. Item #6 must then restore the scroll on the new element. Pick this or #72.

**Depends on.** [#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated).

**Sources.** [MDN on @starting-style](https://developer.mozilla.org/en-US/docs/Web/CSS/@starting-style),
[web.dev on entry animations](https://web.dev/blog/baseline-entry-animations),
[MDN on live regions](https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Guides/Live_regions),
[Compositing on isolation](https://www.w3.org/TR/compositing-1/#isolation),
[the CSSWG's archive](https://lists.w3.org/Archives/Public/public-css-archive/2020Nov/0451.html),
[Material's motion system](https://material.io/design/motion/the-motion-system.html).


### 76. Make list rows Replicant aliases so unchanged rows aren't expanded

*Rendering · 10 h · gain 2 · score 4 · confidence low*

**Problem.** Rows are plain functions of the whole state
(`elektrolyt/show/ui.cljc:118-135`, `elektrolyt/library/ui.cljc:43-55`,
`elektrolyt/library/inbox/ui.cljc:35-39`, `elektrolyt/library/queue/ui.cljc:121-131`,
`elektrolyt/search/ui.cljc:127-142`). So every render builds every row's Hiccup,
with its thumbnails, dates, hrefs and ids, before Replicant finds most of them
unchanged with a deep =. Replicant's answer is aliases, which it doesn't expand
while their attributes and children are = (`replicant/core.cljc:911-916`), and
the app uses them only for icons and buttons
(`elektrolyt/ui/controls.cljc:22, 111, 124`). The Library's renders also re-sort
every show (`elektrolyt/library/shows.cljc:45-52`).

**Fix.** First the cheap step, about 2 h, which stands alone: memoize
`shows/sorted` on `[all sort]` and the Library's seq of tiles on `[all sort
(:colors state)]`, which gives the biggest list identical Hiccup between
renders. Then turn the rows that change one at a time into defaliases whose
attributes are only their data and what they need from the state: Inbox and
Queue rows with their neighbour's focus target, the Show page's episode rows and
search results, e.g. `show-item` takes `{:show show :dominant colour}`. Keep
`:replicant/key` on the li. Test by expanding with `replicant.alias/expand`, as
controls_test does, and check each list's scenes and the e2e focus moves after
Dismiss, Remove and Move.

**Effort and gain.** About 2 h for the Library's memo, then about 8 h to narrow
five row views. Renders of long lists skip unchanged rows, e.g. a big Library
during a refresh's progress. Unmeasured, and it overlaps #9 and #12.

**Risks and principles.** The narrowing at each call site must be complete, or
a row shows stale data. Aliases are Replicant's own mechanism for this, and
ui.memo is the app's.

**Sources.** [Replicant on aliases](https://replicant.fun/alias/),
[Replicant on keys](https://replicant.fun/keys/),
[Replicant, top-down](https://replicant.fun/top-down/).


### 77. Show the last session's page from a saved snapshot until the app renders

*Perceived · 12 h · gain 2 · score 4 · confidence low*

**Problem.** At every start, #app holds only the noscript until the whole
library is read, /api/server has answered and the cached colours are read
(`elektrolyt.cljs:472-482`), and podcast-clj's `read!` fetches every feed and
index document (`podcast/app/library.cljc:2182-2238`). A listener who reopens
Home, the Queue or the Library sees blank paper, or #10's placeholders.

**Fix.** In runtime.local, add a writer that the existing `on-hidden!` handler
calls (`elektrolyt.cljs:465`). On Home, the Queue or the Library alone, it keeps
#app's innerHTML in localStorage with the path and the time, unless it's over
about 150 KB. It first removes the audio, the dialog and the announcer's text,
and it skips a page with opened sections, since those don't survive a load
(STATE.md:46). Add a blocking public/shell.js right after #app (the CSP allows no
inline script, `elektrolyt/server.clj:279`) that puts a snapshot for the same
path, under about 24 h old, into #app inside an inert wrapper, with a visible
sign that it doesn't respond yet. Replicant's first render clears it in one task
(`replicant/dom.cljs:203-204`). Add a localStorage row and an entry under 'Facts
in two places' to STATE.md, e2e cases for a matching path, another path, a stale
snapshot, a reduced page and no storage, and check that NVDA and VoiceOver hear
nothing until the render.

**Effort and gain.** The writer's rules, shell.js, five e2e cases in two
browsers, screen reader checks and STATE.md, with time to tune the cases that
jump. It replaces placeholders with last time's content for the same wait, and
doesn't make the page usable any sooner.

**Risks and principles.** Changed content jumps at the swap. Taps on a page that
looks live but is inert do nothing. A synchronous read and parse of up to
150 KB comes before the first paint. It's in tension with views as pure
functions of the state and with one writer per fact, since it keeps nearly every
fact on the page a second time, though it isn't server rendering. That's your
call, and #85 is the alternative within the principles.

**Depends on.** [#10](#10-paint-the-shells-frame-and-placeholders-from-indexhtml).

**Sources.** [Perfplanet on instant web apps](https://calendar.perfplanet.com/2016/instant-web-applications/),
[MDN on inert](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Global_attributes/inert),
[Chrome on the Page Lifecycle](https://developer.chrome.com/docs/web-platform/page-lifecycle-api).


### 78. Morph the Library's tiles between list and grid, and cross-fade the theme

*Motion · 4.5 h · gain 1 · score 3 · confidence medium*

**Problem.** Changing the Library's 'Show as' or 'Sort by' rebuilds the layout
in a frame (`elektrolyt/library/ui.cljc:12-20, 71-79`;
`elektrolyt.cljs:275-277`), and switching the theme or the contrast flips every
colour at once (`elektrolyt/ui/document.cljs:34-42`).

**Fix.** Add `runtime/motion?` and `runtime/transition!`, which calls
document.startViewTransition with its function when the API exists and motion is
allowed, and calls the function alone otherwise. Wrap the swap in
`:library/view`, and `:settings/prefer` only for appearance and contrast
(`elektrolyt.cljs:344-350`). Name the artwork rather than the article:
`.library-show img { view-transition-name: match-element; view-transition-class:
library-artwork }`, and the h3 too if you like. A named .library-show would be
a stacking context at all times, which isolates it, so its artwork would stop
multiplying into the paper in the light theme (`public/css/look.css:579-582`);
the img is already a stacking context through its opacity. Add
`::view-transition { pointer-events: none }` and groups of 0.25 s, and check
whether the snapshots keep the multiply. Add e2e tests with and without the API
and with reduced motion.

**Effort and gain.** Two runtime functions, two effects, CSS, a look in three
browsers and the e2e tests. Polish on deliberate, occasional changes.

**Risks.** The match-element value needs Chrome 137, Safari 18.4 and Firefox
144, and older browsers update at once. Many snapshots in a big library strain a
phone.

**Depends on.** [#57](#57-run-the-e2e-tests-with-reduced-motion-and-check-that-all-motion-stays-gated).

**Sources.** [web.dev on same-document view transitions](https://web.dev/blog/same-document-view-transitions-are-now-baseline-newly-available),
[Chrome on view transitions](https://developer.chrome.com/docs/web-platform/view-transitions/same-document),
[MDN's compatibility data for view-transition-name](https://raw.githubusercontent.com/mdn/browser-compat-data/main/css/properties/view-transition-name.json),
[CSS View Transitions on view-transition-name](https://drafts.csswg.org/css-view-transitions-1/#view-transition-name-prop),
[Compositing Level 1](https://www.w3.org/TR/compositing-1/),
[master.dev on unclickable view transitions](https://blog.master.dev/view-transitions-careful-not-to-make-stuff-unclickable/).


### 79. Time renders, library operations and worker codec work with User Timing

*Measurement · 5 h · gain 1 · score 3 · confidence high*

**Problem.** Most costs in this report are estimates, and the app times nothing:
not the renders, whole and synchronous at every swap
(`elektrolyt.cljs:479-480`); not the library's operations
(`elektrolyt/library.cljs:113, 187, 217, 249`;
`elektrolyt/library/refresh.cljs:120-127`); and not the page's encode and
decode of the worker's feeds (`elektrolyt/worker/fetching.cljs:221-230,
281-289`). A DevTools recording of the dev build names functions, but nothing
lasting records which renders or operations are slow in daily use, and Firefox's
profiler sees none of it.

**Fix.** Add a runtime namespace, runtime.perf, with `(measured! name props
f)`. It reads performance.now around f and calls performance.measure with a
devtools detail, so that Chrome shows a track named Elektrolyt and Firefox's
profiler a UserTiming marker, and clears the measures of repeating ones such as
renders and playback steps.

- Turn it on with `(or ^boolean goog/DEBUG enabled?)`, where enabled? is a
  goog-define that defaults to false: on in dev, gone in release, and on in a
  profiling release that sets it.
- Wrap the `::render` and `::colors` watches with the route and a pure, tested
  `changed-keys` of the top-level keys, since `watch!` passes the old state
  (`elektrolyt/runtime.cljs:184-188`).
- Wrap `change!` by kind of operation, `written!`, `caught-up!`, `open!`,
  `refreshed!`, and the worker's encode and decode with the string's length. In
  the worker, call performance.measure directly.
- Flag more than one render before a paint with a counter that rAF resets.
- Log `:perf/slow-render` over 16 ms at info and every render at debug, with
  keywords and numbers only.

Add a STATE.md row and a research comment.

**Effort and gain.** One namespace, a tested `changed-keys`, about eight wrapped
sites, and checks in both profilers. Nothing a listener sees, but it ranks #12
against #9, #26 and #30, and checks each once it's built.

**Risks.** Measures that startup or the e2e tests read must not be cleared. Dev
numbers overstate release. It times the JS and DOM work, not style, layout or
paint.

**Sources.** [Chrome on extending the Performance panel](https://developer.chrome.com/docs/devtools/performance/extension),
[MDN on User Timing](https://developer.mozilla.org/en-US/docs/Web/API/Performance_API/User_timing),
[MDN on performance.measure](https://developer.mozilla.org/en-US/docs/Web/API/Performance/measure),
[Firefox on instrumenting JavaScript](https://firefox-source-docs.mozilla.org/tools/profiler/instrumenting-javascript.html),
[Telemere](https://github.com/taoensso/telemere).


### 80. Build an open transcript's and chapter list's Hiccup once, not every render

*Rendering · 3 h · gain 2 · score 8 · confidence low*

**Problem.** While the transcript is open, every render, i.e. every second
during playback, merges its segments into paragraphs again
(`elektrolyt/episode/ui.cljc:232-259`; `podcast/format/transcript.cljc:337-361`).
It also builds a time link per paragraph, each with a SHA-1 of the same guid, a
slug of the same title and a reitit route (`episode/ui.cljc:29-37`;
`elektrolyt/ui/routes.cljc:120-133`), and Replicant then compares the rebuilt
subtree node by node. The chapters do the same on a smaller scale. An hour's
transcript has roughly 60–200 paragraphs.

**Fix.** Measure first with #79, with the transcript open. Then memoize the
transcript's div with `memo/remember-last` on `[document page lang]`. The
`page-in` function returns the identical page while its feed, URL, key, guid,
hash and slug stay (`elektrolyt/episode.cljc:103-110`), so the page's identity
covers every field the links use, including the id that changes after
subscribing. Replicant then skips the identical subtree. Build each link's href
from the page's `:href` plus t, as '?t=' or as '&t=' after ?url=, in
`episode-href`'s order, through a helper in ui.routes that a test pins equal to
`episode-href`. Do the same for the chapters, keyed on `[chapters page lang]`.
Add tests in episode/ui_test.cljc that two renders that differ only in time
return identical Hiccup, and look at the transcript's scene.

**Effort and gain.** Two memos and a helper for hrefs, tests of identity and
links, and a look at a scene. Likely several ms a second on a phone for long
transcripts, on an occasional path; #9 covers the per-second part more widely.

**Risks.** One slot holds the last transcript, so switching between two
recomputes. The appended t must match reitit's encoding exactly.

**Depends on.** [#79](#79-time-renders-library-operations-and-worker-codec-work-with-user-timing),
so it sits just below it.

**Sources.** [Replicant, top-down](https://replicant.fun/top-down/),
[Replicant on aliases](https://replicant.fun/alias/),
[the WAI on transcripts](https://www.w3.org/WAI/media/av/transcripts/).


### 81. Make the queue, inbox and shows cheap to recompute at each progress report

*Rendering · 6 h · gain 2 · score 6 · confidence low*

**Problem.** The queue, the inbox and the Library's shows are memoized on the
feeds and the whole profile (`elektrolyt/library/shows.cljc:32-35`,
`elektrolyt/library/inbox.cljc:21-24`, `elektrolyt/library/queue.cljc:24-27`),
so every 15 s step, pause, seek and catch-up recomputes them. The queue is
rebuilt on every page, since the player's Next link reads it
(`elektrolyt/player/ui.cljc:248-252`). It costs O(queued episodes × items of
their feeds), because podcast-clj finds an item by a linear scan of its feed
(`podcast/app/library/impl.cljc:678-687`), twice per row
(`podcast/app/library.cljc:1255-1272`). The keys can't leave positions out:
rows carry the position and the time left, which the Queue shows
(`elektrolyt/library/queue/ui.cljc:26-31, 74-77`), and the shows' counts depend
on positions (`podcast/profile.cljc:636-642`). Nobody has timed it. The inbox
isn't computed on every page, though its docstring says so.

**Fix.** Measure first, with #79's render measures and #43's library. Keep the
app's keys, and fix the cost in podcast-clj:

- Look items up in an index from item guid to item per feed, cached by the
  identity of the feed's entry (a single slot or a small LRU in CLJC), which
  makes the queue and the inbox O(rows).
- Count each show's played and in-progress episodes from the profile's entries
  grouped by feed, with the count of distinct items memoized per feed entry.

Tests on both platforms pin equal results. If the queue still costs a frame,
split its rows in the app: memoize the facts that don't change on `[(:feeds lib)
queue ids]`, and merge the position and time left at each render. Correct
inbox.cljc's docstring.

**Effort and gain.** An index and a change of counting in podcast-clj with
tests, a release and a bump, and perhaps a split in the app. It could remove a
periodic task during playback on every page. Untimed.

**Risks and principles.** The index must keep the first match for duplicate
guids and include live items. The lookup and the counting are pure functions of
podcast-clj, so they go there, while a split of rows is app policy.

**Depends on.** [#79](#79-time-renders-library-operations-and-worker-codec-work-with-user-timing)
and [#43](#43-build-a-benchmark-library-of-50-shows-for-repeatable-profiling).
It first named a dev logger for memos that was dropped in favour of #79.

**Sources.** [web.dev on INP](https://web.dev/articles/inp),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[Replicant, top-down](https://replicant.fun/top-down/).


### 82. Time each Nexus dispatch and count its renders in dev

*Measurement · 2 h · gain 1 · score 4 · confidence high*

**Problem.** A slow click doesn't say which action made it slow or how many
renders its effects ran, since each store effect swaps and renders inside the
effect (`elektrolyt.cljs:94-100, 463`); Show more, for one, renders twice
(`elektrolyt/show.cljc:343-350`). Nexus's own action log times dispatches,
actions and effects (`nexus/inspector.cljc:85-87, 436-551` in 2026.08.1), but
it needs Dataspex, which deps.edn lacks, and counts no renders.

**Fix.** When runtime.perf is on, register one interceptor. Its before-dispatch
notes performance.now and #79's render count when the context has no trace, i.e.
for a top-level dispatch. Its after-dispatch logs `:perf/slow-dispatch` with the
actions' keywords, the ms and the renders when it takes over 50 ms or more than
one render, and makes a User Timing measure. Add a pure summarizer with tests.
Document that async work after a promise isn't seen, and that dispatches on
mount nest inside the outer render. Mention `nexus.action-log/inspect` with
Dataspex as the fuller view.

**Effort and gain.** One interceptor and a tested summarizer. It names the
action behind a slow click and how many renders it ran, which #12 needs.

**Risks.** It must never log the actions' arguments, which hold titles and URLs.

**Depends on.** [#79](#79-time-renders-library-operations-and-worker-codec-work-with-user-timing).

**Sources.** [Nexus](https://github.com/cjohansen/nexus),
[Dataspex](https://github.com/cjohansen/dataspex),
[web.dev on long tasks](https://web.dev/articles/optimize-long-tasks).


### 83. Profile a release build, with the listener's library, not the dev build

*Measurement · 3 h · gain 1 · score 4 · confidence medium*

**Problem.** Every number so far comes from the dev build, which has no Closure
optimizations and adds Replicant's per-node asserts and dev keys
(`replicant/env.clj`, `replicant/assert.cljc:12-14`), so fixes ranked by dev
timings may chase costs that release doesn't have. A release on a port of its
own is a different origin, whose IndexedDB lacks the library where you see the
seconds.

**Fix.** Item #8 covers most of this: its release folder and its serving on the
same origin give you a release with your library. What's left is a `:bench`
build, `:app` with its own output folder and closure-defines that turn
runtime.perf on, released through the running nREPL with pseudo-names, served
the same way and profiled at 4x CPU throttling in Chrome, and in Firefox and
Safari too. If you serve it on another port, bring the library over by OPML,
which keeps the subscriptions but not the history or positions. The worker's
URL is absolute (`elektrolyt/worker/fetching.cljs:254`), so the build must sit
at its origin's root. Add a README section.

**Effort and gain.** A build entry and a README section, plus whatever #8
leaves. It corrects the baseline that every measurement ranks fixes by.

**Risks.** A release in the serve JVM takes a minute and memory.

**Depends on.** [#79](#79-time-renders-library-operations-and-worker-codec-work-with-user-timing).

**Sources.** [Replicant's build options](https://replicant.fun/build-options/),
[the shadow-cljs guide](https://shadow-cljs.github.io/docs/UsersGuide.html),
[setCPUThrottlingRate](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setCPUThrottlingRate),
[MDN on the same-origin policy](https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy).


### 84. Mark each startup stage and log the time to the first render

*Measurement · 3.5 h · gain 1 · score 4 · confidence high*

**Problem.** Nothing measures startup: the time from navigation to the first
render, the library read's shares of IndexedDB and decoding, /api/server, the
colours' cache and the first refresh. The first render waits for them one after
another (`elektrolyt.cljs:453-482`; `elektrolyt/library.cljs:249-265`;
`elektrolyt/ui/artwork/colors.cljs:92-103`), and the e2e library is one show of
three episodes (`test/e2e/fixtures.mjs:22-35`).

**Fix.** Add `mark!` to runtime.perf, and mark init's start, the library read,
the server's answer, `kept-colors!`, the first render, the first paint through
`painted!`, and the refresh's start, answer and application. These marks are
never cleared and stay in every build. Split the library read without touching
podcast-clj: the app passes its own store to `read!` (`library.cljs:259-261`),
so pass `(update store :fetch timed)` and take the IndexedDB time from the first
fetch to the last, leaving the rest to decoding and indexing. Observe FCP and LCP
in all three browsers, and LoAF in Chromium for the first refresh. Log
`:perf/startup` with each stage's ms and the count of feeds. An e2e test checks
that the marks exist in order, and a baseline for a large library uses #43. Add
a README note.

**Effort and gain.** A dozen marks, a wrapped fetch, one observer, an e2e check
and a note. It decides whether #10, #11, #36, #85, #86 and #89 are worth what
they cost.

**Risks.** LCP stops at the first input. Dev marks include shadow's loading in
dev.

**Depends on.** [#79](#79-time-renders-library-operations-and-worker-codec-work-with-user-timing).

**Sources.** [MDN on User Timing](https://developer.mozilla.org/en-US/docs/Web/API/Performance_API/User_timing),
[web.dev on custom metrics](https://web.dev/articles/custom-metrics),
[web.dev on LCP and INP in Baseline](https://web.dev/blog/lcp-and-inp-are-now-baseline-newly-available),
[Chrome on Long Animation Frames](https://developer.chrome.com/docs/web-platform/long-animation-frames).


### 85. Render the page's frame at init, before the library is read

*Startup · 4.5 h · gain 2 · score 6 · confidence low*

**Problem.** Once the JS has run, nothing renders until IndexedDB returns the
whole library, /api/server answers and the colours' cache is read
(`elektrolyt.cljs:453-482`), so there's no heading, current link or window
title. The views can't render earlier as they are: without a library they show
'Library not read' on library pages, Home puts About first, and without a route
they render 'There's no page here' (`elektrolyt/ui.cljc:203-213, 225-228`;
`elektrolyt/ui/home.cljc:63-65`).

**Fix.** Right after `r/set-dispatch!` in init, call `document/render!` once
with `(assoc @store :route (routes/match->route (reitit/match-by-path
routes/router js/location.pathname)) :starting? true)`. That value never enters
the store, so the router's first navigation stays the first, and doesn't move
the focus (`elektrolyt/ui/routes.cljc:159-169`). While starting, `main-content`
renders the page's heading and `ui/skeleton`, shared with #10 if that lands, and
nothing that needs the library or the server. Give .shell a `:replicant/key` of
`:starting` in this frame, so that the real first render makes a new .shell that
opens in its colours without the seed transition. Dropping a data attribute in
that render wouldn't stop the transition, since transitions take the values of
the style after the change. Catch nav taps before the router starts, with #10's
shell.js or a listener of your own that pushes the state and renders the frame
again. Add unit tests per route, an e2e test with /api/server delayed, and a
scene.

**Effort and gain.** One branch in `main-content`, a few lines in init, the keyed
shell, the capture of clicks and the tests. On its own it shows the frame with
the right heading, link and title during the library read, likely the longest
part of the blank; after #10 it adds only the heading and the title. The read's
length is unmeasured.

**Risks.** An extra small render in init's first task. The bars stay in the
app's own colours until the cached ones arrive.

**Depends on.** [#84](#84-mark-each-startup-stage-and-log-the-time-to-the-first-render),
so it sits just below it.

**Sources.** [web.dev on FCP](https://web.dev/articles/fcp),
[Chrome on the app shell](https://developer.chrome.com/blog/app-shell),
[NN/g on skeleton screens](https://www.nngroup.com/articles/skeleton-screens/),
[CSS Transitions on starting](https://drafts.csswg.org/css-transitions-1/#starting).


### 86. Decode each feed document as its fetch resolves in podcast-clj's read!

*Startup · 3 h · gain 1 · score 4 · confidence low*

**Problem.** In podcast-clj, `read!` fetches the library's document, then every
feed document and segment at once (`podcast/app/library.cljc:2199-2218`), and
decodes them all in one synchronous `from-saved` only after the last one
arrives (`library.cljc:2122-2147, 2229-2237`). The main thread waits during the
reads and decodes afterwards, so the blank lasts the I/O plus the decoding,
which could overlap. The saving is at most the time to decode the feeds, and
Transit decodes fast: 38 ms for 20 MB in Chrome
(`podcast/app/library/impl.cljc:1131-1132`).

**Fix.** Measure first with #84 on a library of about 100 feeds, and go on only
if the shorter phase takes about 50 ms or more. In `read!`, decode a feed
document with `impl/read-feed` in its own fetch's then, as `with-feeds` does
(`impl.cljc:1307-1321`), keep the decoded reads by document name, and give
`from-saved` a private arity that takes them. The index stays at the end, since
`ciff/read-files` reads every segment at once. The documents are named by their
content (`library.cljc:2048-2050`), so a retry keeps the decodes it made. Add
tests for the retry and for unreadable documents, release, and pin it.

**Effort and gain.** One function and a private arity, tests and a release.
Perhaps 50–200 ms of blank in a big library, and barely anything in a small one.
Unmeasured.

**Risks.** The consistency rules of `read!` must hold with decodes that run
before the attempt ends. It overlaps your podcast-clj work, so wait for it or
rebase onto it.

**Depends on.** [#84](#84-mark-each-startup-stage-and-log-the-time-to-the-first-render).

**Sources.** [Nolan Lawson on IndexedDB's speed](https://nolanlawson.com/2021/08/22/speeding-up-indexeddb-reads-and-writes/),
[RxDB on slow IndexedDB](https://rxdb.info/slow-indexeddb.html),
[the IndexedDB spec](https://www.w3.org/TR/IndexedDB/).


### 87. Log slow interactions and long frames with PerformanceObserver

*Measurement · 4 h · gain 1 · score 4 · confidence medium*

**Problem.** You see seconds where this session measured 281–674 ms, and nothing
in your browser records which interaction was slow, for how long or in which
phase. Other daily freezes, like the refresh applied when the tab becomes
visible (`elektrolyt.cljs:467`; `elektrolyt/library/refresh.cljs:120-127`), go
unnoticed. The subscribe work now runs in a later task, after `painted!`
(`elektrolyt/show/feeds.cljs:300-321`), so the click paints fast and Event
Timing can't see that freeze; only Chromium's Long Animation Frames can.

**Fix.** Add `observe!` to runtime.perf:

- Watch 'event' entries (a durationThreshold of 16, buffered), grouped by
  interactionId and split into input delay, processing and presentation delay.
- Where supported, watch 'long-animation-frame' entries with their blocking
  duration and, per script, the invoker, the source and the forced style and
  layout. In Chromium, also watch 'layout-shift'.
- Log `:perf/slow-interaction` over 100 ms, and `:perf/long-frame` for a frame
  blocked over 100 ms with no interaction in it, with the event's type, the
  route and a target described by tag and id alone, since aria-labels carry
  titles.
- Keep the 20 worst in an atom, with an exported report that returns JSON to
  paste.

Add PerformanceObserver to runtime_test's pattern, a STATE.md row and a tested
summarizer.

**Effort and gain.** About 80 lines, a tested summarizer, the pattern, a row and
checks in three browsers. In Chromium it turns 'several seconds in my browser'
into phases and an entry point, and elsewhere into phases alone.

**Risks.** Event Timing rounds durations to 8 ms. Long Animation Frames often
name an anonymous promise callback, so the hot function still needs a profile.

**Depends on.** [#79](#79-time-renders-library-operations-and-worker-codec-work-with-user-timing).

**Sources.** [web.dev on INP](https://web.dev/articles/inp),
[Chrome on Long Animation Frames](https://developer.chrome.com/docs/web-platform/long-animation-frames),
[MDN on PerformanceEventTiming](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceEventTiming),
[MDN on interactionId](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceEventTiming/interactionId),
[MDN on PerformanceScriptTiming](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceScriptTiming),
[Mozilla on Firefox 144's interactionId](https://blog.mozilla.org/performance/2025/10/15/firefox-144-ships-interactionid-for-inp/),
[web.dev on LCP and INP in Baseline](https://web.dev/blog/lcp-and-inp-are-now-baseline-newly-available).


### 88. Add performance budgets to the end-to-end tests for the key flows

*Measurement · 9 h · gain 2 · score 4 · confidence medium*

**Problem.** Nothing stops a change from bringing a freeze back. The e2e tests
check behaviour alone, and naive assertions of time would flake: the same
subscribe took from 281 to 674 ms here.

**Fix.** Write a perf fixture whose init script records Event Timing entries in
all three engines, a counter of gaps between rAF callbacks (frames over 50 ms
and the longest gap), and in Chrome the long-animation-frame entries, with 4x
CPU throttling through CDP. A perf.spec.mjs runs on #43's library with one
worker against #83's build: open a Show page of 2,000+ episodes, Subscribe, go
back and forth between the Library and a Show page, Show more, play for 10 s,
and Check for new episodes. Assert what the fixes guarantee: the Subscribe
click's event duration under 200 ms, which `painted!` provides, and the longest
frame in each flow under a cap of about three times the median of five
calibration runs. Run it as npm run perf in a CI job of its own, which doesn't
block at first, with traces on failure.

**Effort and gain.** A fixture, five scenarios, calibration in two browsers, a
config and a CI job. It guards the fixes for freezes against regressions.

**Risks.** Flakiness, which medians and wide caps reduce. Throttling and Long
Animation Frames are Chromium only, and whether CDP's throttling slows the
worker too is unverified.

**Depends on.** [#43](#43-build-a-benchmark-library-of-50-shows-for-repeatable-profiling)
and [#83](#83-profile-a-release-build-with-the-listeners-library-not-the-dev-build),
so it sits below #83.

**Sources.** [Playwright's CDPSession](https://playwright.dev/docs/api/class-cdpsession),
[setCPUThrottlingRate](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setCPUThrottlingRate),
[MDN on PerformanceLongAnimationFrameTiming](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceLongAnimationFrameTiming),
[MDN on PerformanceEventTiming](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceEventTiming),
[MDN's compatibility data for long animation frames](https://github.com/mdn/browser-compat-data/blob/main/api/PerformanceLongAnimationFrameTiming.json)
and [for Event Timing](https://github.com/mdn/browser-compat-data/blob/main/api/PerformanceEventTiming.json),
[web.dev on INP](https://web.dev/articles/inp).


### 89. Read the first page's feeds first and load the rest of the library after

*Startup · 18 h · gain 2 · score 4 · confidence low*

**Problem.** At start the page is blank until `read!` has fetched and decoded
every feed document and the whole index (`elektrolyt/library.cljs:259-261`). A
deep link to an Episode page, a subscribed show or the Queue needs only a few
feeds. Home, the usual first page, needs every feed for its picks
(`podcast/app/library.cljc:1491-1520`), so there only the queue would show
sooner.

**Fix.** Measure the read's fetch and decode times on a big library first. In
podcast-clj, give `read!` a `:load` option that reaches `needs`, `from-saved`'s
read and the retry check (`podcast/app/library.cljc:2082-2105, 2122-2127,
2232`). A feed left out must keep its document entry unloaded and never be cut
to its URL, which `with-read-feeds` would do
(`podcast/app/library/impl.cljc:1292`). In the app, `open!` reads the playing
episode's feed, the queue's feeds and the route's show, renders, and loads the
rest with `load!` at idle; `caught-up!` and the save's read on a conflict pass
the same `:load` until that's done. The views that walk every feed (Home's
picks, the Library, the Inbox, and the picks on Show and Episode pages) show
their section with aria-busy and a placeholder of fixed height until everything
is loaded, and fill in silently. Add a STATE.md row, scenes, and an e2e test
that deep-links into a big fixture library.

**Effort and gain.** The interplay of `read!`, `needs` and `from-saved` with
tests for retries and other tabs' writes, the open in two phases, placeholders
in four or more views, an e2e test and a release. Only a big library gains: a
deep link could open in a fraction of the time, while Home shows the queue
sooner and the picks later.

**Risks and principles.** A partial read that misses one of those paths
silently cuts feeds to their URL, and the next write deletes their documents,
which loses data until the next refresh. It reverses init's documented choice to
open the page complete (`elektrolyt.cljs:453-461`), which is your call, and it
adds a partly loaded state to STATE.md.

**Depends on.** [#84](#84-mark-each-startup-stage-and-log-the-time-to-the-first-render)
and [#86](#86-decode-each-feed-document-as-its-fetch-resolves-in-podcast-cljs-read).

**Sources.** [web.dev on long tasks](https://web.dev/articles/optimize-long-tasks),
[Chrome on the app shell](https://developer.chrome.com/blog/app-shell),
[WAI-ARIA on aria-busy](https://www.w3.org/TR/wai-aria-1.2/#aria-busy),
[web.dev on CLS](https://web.dev/articles/cls).


### 90. Measure long-list scrolling on phones in Chrome, Firefox and Safari

*Measurement · 4.5 h · gain 1 · score 3 · confidence high*

**Problem.** Nobody has measured scroll frame times, paints on the main thread
or layer counts on a phone for the Library grid, a long Show page or the Inbox,
which lists every episode (`elektrolyt/library/inbox/ui.cljc:142-153`). Items
#91 and #92 rest on engine source. The app has no wheel or touch listeners
(`elektrolyt/runtime.cljs:106-176`), so blocking input handlers aren't the
cause. Firefox removed paint flashing from its DevTools in version 96.

**Fix.** On #43's library and a release build (#8 or #83), in light and dark,
fling for 5 s through the Library grid, the Inbox and a Show page expanded past
200 episodes.

- On a mid-range Android phone in Chrome, with adb reverse so the Host stays
  localhost: paint flashing, layer borders, frame stats and the Layers panel's
  compositing reasons, and a trace that counts frames over 16.7 ms.
- In Firefox for Android, the Firefox Profiler from a distance, counting Paint
  and Rasterize markers per second with apz.paint_skipping.enabled on and off.
- On an iPhone, Safari's Layers tab and a Rendering Frames timeline. That needs
  the release build served where the iPhone can reach it, e.g. a static copy of
  public/ on the LAN with fixture data.

Record the devices, the date and the numbers in a page in doc/, and run it again
after #91 and #92.

**Effort and gain.** Remote debugging on three devices, flings in three browsers
and two themes, serving the build to an iPhone, and the write-up. It decides
whether #91 and #92 are worth building, and gives them numbers before and after.

**Risks.** Debug overlays and remote inspectors cost frames, so take frame times
with the overlays off. A copy for the iPhone without the proxy has no live
feeds.

**Depends on.** [#43](#43-build-a-benchmark-library-of-50-shows-for-repeatable-profiling)
and [#83](#83-profile-a-release-build-with-the-listeners-library-not-the-dev-build).

**Sources.** [Chrome on rendering performance](https://developer.chrome.com/docs/devtools/rendering/performance),
[Chrome's Layers panel](https://developer.chrome.com/docs/devtools/layers),
[Chrome on remote debugging a local server](https://developer.chrome.com/docs/devtools/remote-debugging/local-server),
[the Firefox Profiler on remote profiling](https://profiler.firefox.com/docs/#/./guide-remote-profiling),
[Mozilla bug 1743310](https://bugzilla.mozilla.org/show_bug.cgi?id=1743310),
[WebKit's Layers tab](https://webkit.org/web-inspector/layers-tab/).


### 91. Print main's grain on a page wrapper instead of a local background

*Rendering · 5 h · gain 3 · score 9 · confidence medium*

**Problem.** The paper's grain is main's own background, with
background-attachment: local (`public/css/look.css:281-290`), and main is the
only scroller (`public/css/layout.css:252-262`). Chrome paints that into the
scrolling contents at no cost. Safari repaints main's whole visible area at
every scroll update of a scroller with a local image background, and its own
commit admits that the background can lag the scroll (WebKit's b6af623). Firefox
turns off its scroll paint skipping for such a scroller (bug 1267656), so each
update schedules a paint on the main thread. That's extra paint work on a phone
during the most frequent interaction, on the Library, the Inbox and long Show
pages. The scroll itself stays on the compositor, so the cost is main-thread
time, battery and a grain that jitters. Unmeasured.

**Fix.** In ui.cljc's `main-content`, wrap the save notice, the heading and the
page's content in a `[:div.paper …]`, and keep the announcer and the visually
hidden notes as main's direct children, which keeps
`test/e2e/fixtures.mjs:83` valid. In look.css, move the grain to `.shell > main
> .paper` as a plain background, keep the accent tokens on main, and cite the
WebKit and Firefox sources. In layout.css, move main's padding
(`layout.css:260, 343-346`) and its container-type (`layout.css:261`) to the
wrapper, so that container queries keep their widths, and give the wrapper
min-block-size: 100%, so that short pages keep their grain. Update the measure's
rule at `look.css:296-299`, and wrap the Portfolio scenes that build main#main.
Measure before and after with #90.

**Effort and gain.** One wrapper, about six CSS rules and the scenes. The time
goes to checking container queries, the measure, scrollIntoView, the announcer
and the skip link, to an e2e assertion, and to measuring in Safari and Firefox
on phones. Scrolling is the most frequent interaction, and by their source, two
of the three browsers repaint the whole visible list at every scroll update.

**Risks and principles.** Selectors and tests that assume main's direct
children. On iOS, overscrolling past the wrapper's edge shows paper without
grain. It adds a presentational div, so check the main landmark with axe and
VoiceOver.

**Depends on.** [#90](#90-measure-long-list-scrolling-on-phones-in-chrome-firefox-and-safari),
which measures it. That's what moved it down from a score of 9. The engine
sources are strong, so if you're happy to measure afterwards, you can build it
much earlier.

**Sources.** [WebKit's commit b6af623](https://github.com/WebKit/WebKit/commit/b6af623293be604b2cda1b7789258fa230aaa8cd),
[WebKit bug 219324](https://bugs.webkit.org/show_bug.cgi?id=219324),
[WebKit's RenderLayerBacking.cpp](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/rendering/RenderLayerBacking.cpp),
[Mozilla's review of bug 1267656](https://reviewboard.mozilla.org/r/64634/diff/),
[Mozilla bug 2052240](https://bugzilla.mozilla.org/show_bug.cgi?id=2052240),
[Firefox's ScrollContainerFrame.cpp](https://github.com/mozilla-firefox/firefox/blob/main/layout/generic/ScrollContainerFrame.cpp),
[Chromium's layout_box.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/layout/layout_box.cc),
[MDN on background-attachment](https://developer.mozilla.org/en-US/docs/Web/CSS/background-attachment).


### 92. Isolate main so each artwork's multiply stays in its scrolling layer

*Rendering · 4 h · gain 2 · score 8 · confidence medium*

**Problem.** In the light theme, every img in main is drawn with mix-blend-mode:
multiply at 90% opacity (`public/css/look.css:573-582`;
`public/css/tokens.css:179-183`). Main isn't a stacking context
(`public/css/layout.css:256-262`), so each img blends against everything up to
the root, the shell's paper and tint outside main included. Chromium folds such
a blend into a layer only when its parent group has at most one layer before it
(`paint_artifact_compositor.cc`), so each artwork stays composited with a render
surface of its own. That's an extra pass per visible artwork per scroll frame in
Chrome on Android, and a count of layers that grows with the rows. Safari
already isolates blending in composited scrollers. Unmeasured.

**Fix.** Beside the print rule, add `.shell > main { isolation: isolate; }`,
citing Compositing Level 1, Chromium's layerizer and WebKit bug 132675. Main's
grain is its own background, so the multiply still prints it into the artwork,
and Chrome can fold each blend into main's scrolling layer. The shell's faint
tint then reaches the artwork only through the 10% that its opacity leaves, as
Safari draws it today. Add a Chromium-only e2e test that counts layers through
CDP on the Inbox with 5 and with 60 episodes in the light theme, and fails if
the count grows with the rows, and a screenshot check of a row. If #90 shows the
isolated group costs Firefox more than it saves, keep the multiply on the
single intro artwork alone.

**Effort and gain.** One rule with a comment; the CDP test, screenshots in three
browsers and Firefox's frame times take the rest. From engine source, and a
dozen small surfaces is probably a modest cost for the GPU.

**Risks.** Firefox might draw an isolated scroller off-screen instead of from
cached tiles. Search's own scroller inside main keeps its results' blends
composited.

**Depends on.** [#90](#90-measure-long-list-scrolling-on-phones-in-chrome-firefox-and-safari).

**Sources.** [Chromium's paint_artifact_compositor.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/platform/graphics/compositing/paint_artifact_compositor.cc),
[Chromium's property_tree_manager.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/platform/graphics/compositing/property_tree_manager.cc),
[Chromium's paint_property_tree_builder.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/paint/paint_property_tree_builder.cc),
[WebKit bug 132675](https://bugs.webkit.org/show_bug.cgi?id=132675),
[Compositing Level 1](https://www.w3.org/TR/compositing-1/),
[CSS Conditional 5 on container-type](https://drafts.csswg.org/css-conditional-5/#container-type),
[MDN on isolation](https://developer.mozilla.org/en-US/docs/Web/CSS/isolation).


### 93. Measure the heap at start, after ten outside shows and after an hour of play

*Measurement · 5 h · gain 1 · score 3 · confidence high*

**Problem.** Nobody has measured the app's memory, though it's a player left
open for hours, on phones too. Nobody knows how big the heap gets with a real
library, what an outside show or a subscription adds, how long major GCs take
while the clock runs, or whether iOS Safari kills and reloads the tab in the
background. The server sends no COOP or COEP (`elektrolyt/server.clj:286-288`),
so measureUserAgentSpecificMemory isn't available. The dev library in your
Firefox profile is small, so measurements need your real library or #43's.

**Fix.**

1. A sampler in dev only, started from init, that reads Chromium's
   performance.memory at intervals drawn from an exponential distribution with
   a 5-minute mean, and logs `:memory/sampled`. Log document.wasDiscarded, and
   keep the last lifecycle event in localStorage through runtime.local, so that
   a start whose last event was 'hidden' with no pagehide logs
   `:page/reloaded-after-kill` (`elektrolyt/runtime.cljs:119-145`).
2. A protocol in the README's Development section, on a release build with a
   large library: Chrome's heap snapshots at the first idle, after ten outside
   shows and after an hour of play, compared by retained size; a 10-minute trace
   for the major GCs' durations; allocation sampling during `read!`; Firefox's
   about:memory; and Safari's Memory timelines, once an iPhone can reach the app.
3. Optionally, a Chrome-only e2e check through CDP that the heap after GC stays
   under a bound after visiting fixture shows.

**Effort and gain.** About 2 h for the sampler and the marker with a test, 1 h
for the protocol and 2 h for the optional check. It tells whether #46 and #94
are worth building.

**Risks.** Chrome's performance.memory is coarse without a command-line flag, and
dev heaps run high, so take the numbers on release.

**Sources.** [MDN on measureUserAgentSpecificMemory](https://developer.mozilla.org/en-US/docs/Web/API/Performance/measureUserAgentSpecificMemory),
[web.dev on monitoring memory](https://web.dev/articles/monitor-total-page-memory-usage),
[Chrome on heap snapshots](https://developer.chrome.com/docs/devtools/memory-problems/heap-snapshots),
[Firefox's about:memory](https://firefox-source-docs.mozilla.org/performance/memory/about_colon_memory.html),
[WebKit on memory debugging](https://webkit.org/blog/6425/memory-debugging-with-web-inspector/),
[Chrome on the Page Lifecycle](https://developer.chrome.com/docs/web-platform/page-lifecycle-api).


### 94. Keep only the last few outside shows' whole feeds in the store

*Main thread · 5 h · gain 1 · score 3 · confidence low*

**Problem.** Each Show page of an outside show puts the show's whole parsed feed
in `:feeds`, and only Clear cache removes it (`elektrolyt/show.cljc:289-300`;
`elektrolyt/library/settings.cljc:183`). New episodes while the page is open
keep a second copy under `:newer` (`show.cljc:331-341`), and a show you
subscribe to keeps its outside entry too. Ten outside shows in a session keep
ten whole feeds in memory for the tab's life, though the cache already holds the
last 20 for a quick reopen (`elektrolyt/runtime/cache.cljs:34-38`).

**Fix.** In show.cljc, add a pure `forgotten` and a count to keep, e.g. 5. It
keeps the entries of the current route's URL, of URLs with a request in flight
or a subscription under way, and of the five whole feeds shown last. It drops
the others, those of shows now in the library included, and folds `:newer` into
a kept entry that isn't on the current page. Recording when a feed was last
shown needs one fact with one writer, e.g. a short `:feeds-shown` vector, with a
STATE.md row. Dispatch `:feeds/forget` at each new page beside
`:episodes/forget` (`elektrolyt.cljs:196-200`), and swap only when something
goes. Back to a forgotten show goes through `open-show!` and the cache. Add a
unit test, and an e2e test that visits six fixture shows and goes Back to the
first.

**Effort and gain.** The model function, the effect and STATE.md, and an e2e
test with several fixture feeds. It bounds the one store key that grows as the
listener explores. The heap per show is guessed from text sizes, and V8's
concurrent GC makes stalls unlikely.

**Risks.** Back to a show more than five shows ago flashes a short load from the
cache, and in a private window the cache may hold nothing.

**Depends on.** [#93](#93-measure-the-heap-at-start-after-ten-outside-shows-and-after-an-hour-of-play).

**Sources.** [Pocket Casts' PodcastManagerImpl.kt](https://github.com/Automattic/pocket-casts-android/blob/cb128be/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/podcast/PodcastManagerImpl.kt#L218-L235),
[V8, Trash talk](https://v8.dev/blog/trash-talk).


### 95. Measure each script's compile and evaluate time and coverage before splitting

*Measurement · 5.5 h · gain 1 · score 3 · confidence high*

**Problem.** Nobody has measured what parsing, compiling and evaluating the
release scripts costs before the first render, while the page is a blank #app
(`public/index.html:14-20`; `elektrolyt.cljs:475-482`), nor what the worker
pays to import shared.js once more. The dev build says nothing about release,
so whether lazy modules or deferred tables are worth having is open.

**Fix.** Build the release in a throwaway git worktree, never beside your serve
JVM, with source maps, and serve it with the app's server on a free port. A
script outside the e2e testMatch, e.g. test/perf/startup.mjs, uses Playwright's
Chromium with CDP:

- CPU throttling at 4 and 6;
- Tracing with V8's compile categories, summing Compile and Evaluate Script per
  URL for the page and for the worker, which Target.setAutoAttach reaches;
- JS coverage until main renders, mapped to namespaces through the source maps;
- Cold, warm and hot runs, since V8 writes its code cache at the second load.

Repeat it on an Android phone through chrome://inspect, and on an iPhone with
the static release served on the LAN for that run. Write the numbers and a rule
down: split only if code unused at the first render costs main.js more than
about 50 ms of compiling and evaluating at 4x on a hot run, or on the iPhone.

**Effort and gain.** A build in a worktree, about 120 lines of Playwright and
CDP, runs at two rates, a session on a phone of each platform and a write-up. It
decides #96 and any splitting of code.

**Risks.** Trace events change their names between Chrome versions. A copy on
the LAN must not include the proxy, which fetches any URL for whoever reaches
it.

**Sources.** [Chrome on coverage](https://developer.chrome.com/docs/devtools/coverage),
[Playwright's coverage](https://playwright.dev/docs/api/class-coverage),
[setCPUThrottlingRate](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setCPUThrottlingRate),
[V8 on code caching](https://v8.dev/blog/code-caching-for-devs),
[V8, The cost of JavaScript in 2019](https://v8.dev/blog/cost-of-javascript-2019),
[WebKit's Timelines tab](https://webkit.org/web-inspector/timelines-tab/),
[shadow-cljs on web workers](https://shadow-cljs.github.io/docs/UsersGuide.html#_web_workers).


### 96. Build the libraries' load-time tables at first use

*Startup · 4 h · gain 1 · score 4 · confidence low*

**Problem.** The page and the worker both build several tables as shared.js
evaluates, and a top-level call can't move out of a chunk the way a function
can. Among them are feed.spec's registry of elements
(`podcast/feed/spec.cljc:859-893`), the diagnostics catalogues of podcast-clj,
id3-clj and like-minded, which run a regex replace over every doc
(`podcast/diagnostics/impl.cljc:8-18`, `podcast/diagnostics.cljc:47-57`),
html-pieces' map of entities and its tokenizer's scans for the longest name,
colour-coded's spaces, and ical-clj's map of Windows zones. Each is small, and
together they're unmeasured.

**Fix.** Only for the tables that #95's trace shows above about 1–2 ms at 4x:
wrap each in a delay, and read it through a function or @ at the few call sites.
For `feed.spec/default`, which is public and named 14 times in podcast-clj, a
memoized `default-registry` function avoids breaking callers that pass it as
`:registry`. Keep the docs data as it is, since `describe` uses it as a
diagnostic's message. Add tests that the deferred value equals the eager one,
and release once, bottom-up.

**Effort and gain.** Probably one or two libraries, each a small change with a
test, and the releases. Well under a millisecond each on a desktop, and a few
ms together at a slowdown of 4–6x. Unmeasured.

**Risks.** Changing a public var from a value to a delay breaks other apps. The
first deref moves the cost to the first parse or the first diagnostic, which
could land in a click.

**Depends on.** [#95](#95-measure-each-scripts-compile-and-evaluate-time-and-coverage-before-splitting),
so it sits just below it.

**Sources.** [V8, The cost of JavaScript in 2019](https://v8.dev/blog/cost-of-javascript-2019),
[ClojureDocs on delay](https://clojuredocs.org/clojure.core/delay),
[Closure on managing dependencies](https://github.com/google/closure-compiler/wiki/Managing-Dependencies).


### 97. Version the release's scripts by content and cache them as immutable

*Load · 6 h · gain 1 · score 3 · confidence low*

**Problem.** With no-cache on every file (`elektrolyt/server.clj:123-133`), each
load revalidates every script, a round trip each once the server is remote. The
worker's URL is fixed (`elektrolyt/worker/fetching.cljs:254`), so a tab loaded
before a rebuild can start the new worker.js against its old page, and the two
exchange EDN. The `:module-hash-names` option of shadow-cljs doesn't work with
this build's worker: it writes the worker's importScripts with shared's unhashed name before
it hashes the files (`shadow/build/targets/browser.clj:738-781` and
`shadow/build.clj:509-563` in 3.5.7), which thheller confirmed in 2019.

**Fix.** Pick one of two ways. Either give the worker a build of its own, with a
single module, so that `:module-hash-names` applies to both builds, at the cost
of shipping library code twice. Or keep the fixed names, and have a release step
copy each file to a name hashed by its content and rewrite the importScripts
string in worker.js. Either way, the release step writes the release folder's
index.html with the hashed names and the worker's name as data-worker on the
main script, which `web-worker!` reads, falling back to /js/worker.js. The
revalidating interceptor sends a year's immutable caching for hashed names
alone. Keep the previous release's files, so that an open tab's worker still
loads its own version. Add JVM tests of the headers and a unit test of the
rewrite.

**Effort and gain.** A split build or a rename after release, the worker's URL
from the page, a header rule, the previous release kept and an e2e run. A
handful of loopback revalidations per load, and a version skew that needs a
redeploy while a tab is open; more once remote.

**Risks.** A wrong rewrite breaks the worker, and so every refresh. Item #100
would solve both the caching and the skew with a versioned cache, which makes
this mostly redundant if you build that.

**Depends on.** [#8](#8-listen-on-a-release-build-and-run-the-end-to-end-tests-against-it).

**Sources.** [thheller on Clojurians, 2019-12-01](https://clojurians-log.clojureverse.org/shadow-cljs/2019-12-01),
[shadow-cljs on name hashing](https://shadow-cljs.github.io/docs/UsersGuide.html#NameHashing),
[shadow-cljs on the manifest](https://shadow-cljs.github.io/docs/UsersGuide.html#BrowserManifest),
[shadow-cljs on build hooks](https://shadow-cljs.github.io/docs/UsersGuide.html#build-hooks),
[Jake Archibald on caching](https://jakearchibald.com/2016/caching-best-practices/).


### 98. Warm the next episode's redirect chain with a HEAD request before the end

*Network · 6.5 h · gain 1 · score 3 · confidence low*

**Problem.** When an episode ends and the next one autoplays
(`elektrolyt/player/audio.cljs:69-79, 104-110, 379-384`), the next file starts
through its tracking prefixes on cold connections: DNS, TCP and TLS to every
hop, then the host's CDN. The redirects are marked no-store, so no cache skips a
hop, but warm connections shorten each one. With curl, cold against reused
connections to the final first byte: The Daily 1.83 s against 1.65 s, NPR 0.99
against 0.66, Darknet Diaries 0.54 against 0.17, and Omny 1.38 against 0.70.
Nothing warms the next file (`elektrolyt/player/ui.cljc:283-288`, grep).

**Fix.**

1. Add a pure `next-id` to player.cljc that dry-runs `library/step` with
   `[:ended id]` (`podcast/app/library.cljc:1574-1601`) and takes its `[:play
   next]`, falling back to `picks/autoplayed`. A `[:stop]` means nothing to
   warm.
2. Add a pure `warm?` beside `report?` that's true once per episode, when the
   time left before the end, or before the outro, drops below 10 s while it
   plays. That's 10 s rather than 30, since servers close idle keep-alive
   connections after 5–75 s.
3. Add `runtime/warm!`, a no-cors HEAD fetch with credentials, redirects
   followed, no-store and a low priority, for http(s) sources that aren't
   local, ignoring failures.
4. Call it from the reporter (`audio.cljs:194-211`), with the warmed id kept in
   its closure, and not from the hidden hook.
5. Add a STATE.md note and a research comment citing the IAB's exclusion of HEAD
   requests and Flightcast's rule to request the enclosure as published.

Test `next-id` and `warm?`, and add an e2e test in queue.spec.mjs that routes a
chain of 302s and sees a HEAD before 'ended' and the GET after it.

**Effort and gain.** Two pure functions with tests, an effect and a hook, an e2e
fixture and a check in each browser. Heard once per change of episode, by
listeners who queue, mostly with the screen off. The numbers come from curl,
not a browser, and Safari may load media outside the page's pool of
connections.

**Risks.** The prefixes see a HEAD about 10 s before the GET, and one with no
play if the listener stops before the end; the IAB doesn't count a HEAD as a
download. Some hosts answer HEAD along another chain, so the last hop can stay
cold.

**Sources.** [Fetch on CORS-safelisted methods](https://fetch.spec.whatwg.org/#cors-safelisted-method),
[Chromium's client_socket_pool.cc](https://chromium.googlesource.com/chromium/src/+/main/net/socket/client_socket_pool.cc),
[Apache's KeepAliveTimeout](https://httpd.apache.org/docs/2.4/mod/core.html#keepalivetimeout),
[nginx's keepalive_timeout](https://nginx.org/en/docs/http/ngx_http_core_module.html#keepalive_timeout),
[the IAB's counting rules 2.3](https://ppc.land/iab-tech-lab-finalises-v2-3-podcast-counting-rules-covering-video/),
[Flightcast's player recommendations](https://flightcast.com/player-recommendations),
[WebKit bug 221413](https://bugs.webkit.org/show_bug.cgi?id=221413).


### 99. Keep the search index in the worker and ask it for picks

*Main thread · 50 h · gain 3 · score 3 · confidence low*

**Problem.** All of the index's work runs on the page: each feed's documents and
segment when a subscription or refresh applies
(`podcast/app/library/impl.cljc:186-203, 222-239`), the CIFF files that a save
plans, `ciff/read-files` at every load and catch-up (`impl.cljc:1323-1337`;
`podcast/app/library.cljc:2128-2147`), and more-like-this for the picks. Items
#7, #11 and #45 trim parts of it, but adding segments, decoding the index at
open and `recommend` still grow with the library on the main thread. Only the
picks read the index: the app never calls `library/search` or uses a text
criterion (grep), and `recommend` is called only from
`elektrolyt/library/picks.cljc:87`.

**Fix.** Measure first, after #7, #11 and #45: the start, a refresh that lands,
a save and a catch-up in your library. Go further only if they still show long
tasks.

- Stage 1: the worker builds each feed's segment and posts its bytes as a
  transferable ArrayBuffer, which the page splices in. That needs a stable
  numbering of fields and a way to add a segment in drop-in-search.
- Stage 2, only if needed: the page's library takes `:search? false`. The worker
  keeps the index in a database of its own under a Web Lock, follows the
  library's channel and answers requests for picks, which #45 keeps as state.
  The page filters the answers by this tab's waiting operations, and autoplay
  asks the worker at an episode's end. The library's own document holds the
  manifest and names the segments (`podcast/app/library.cljc:2040-2080`), so
  without a database of its own, a save on the page would drop them.

**Effort and gain.** A redesign across podcast-clj (a change of format),
drop-in-search and the app, with tests of several tabs and restarts, and
measurements around each stage. What's left after its dependencies is
unmeasured, and it may be a 4 in a library of thousands of episodes.

**Risks and principles.** The picks and autoplay become asynchronous, also in a
hidden tab on a phone. The worker's profile lags this tab's waiting operations,
and a second copy of the feeds costs memory on phones. Keeping the index in its
own documents changes podcast-clj's format, which is in tension with building
persistence in the app first, while stage 1's new function fits fixing the
library at the source. The index is derived data, so sync doesn't need it.

**Depends on.** [#7](#7-build-the-search-documents-of-a-refreshs-changed-feeds-in-the-worker),
[#11](#11-stop-reading-the-indexs-ids-and-stored-fields-as-edn-per-document-at-open)
and [#45](#45-find-the-picks-after-the-paint-and-keep-them-in-the-store), all
above it.

**Sources.** [Surma, When should you be using workers?](https://surma.dev/things/when-workers/),
[MDN on transferable objects](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API/Transferable_objects),
[MDN on Web Locks](https://developer.mozilla.org/en-US/docs/Web/API/Web_Locks_API),
[MDN on IndexedDB in workers](https://developer.mozilla.org/en-US/docs/Web/API/WorkerGlobalScope/indexedDB),
[MDN on BroadcastChannel](https://developer.mozilla.org/en-US/docs/Web/API/Broadcast_Channel_API).


### 100. Cache the release build's app shell in a service worker

*Load · 14 h · gain 1 · score 2 · confidence medium*

**Problem.** The app can't open without its JVM, though the library is in
IndexedDB and the audio streams from its hosts, and every load asks the server
for every file first. Today the server listens on localhost on your own machine,
so a service worker's startup (about 50–250 ms when it isn't running) costs more
than the loopback requests it saves. Without the server there's still no proxy
for refreshes, search or thumbnails, so for now it's resilience, and speed only
once the server is remote.

**Fix.** Add a public/sw.js in plain JS, registered from a runtime function in
release builds only.

- On install, it caches a versioned shell: index.html, theme.js, the four
  stylesheets, the release's scripts, and Charis Regular and Bold.
- Navigations get the cached index.html, the shell's files come from the cache
  first, and /proxy, /artwork and /api pass through.
- A release step writes the version into sw.js, so the browser sees a changed
  worker. The web worker's script comes through the same cache, so the page and
  the worker always match.
- A waiting service worker doesn't take over when the only tab refreshes, so the
  page offers 'A new version is ready, reload' in its status region, and posts
  skipWaiting when the listener chooses it.

Add a STATE.md row. Set serviceWorkers: 'block' in playwright.config.mjs, since
page.route can't see what a service worker passes through, and add one offline
spec that unblocks them.

**Effort and gain.** The service worker with versioning and a release step,
registration, an accessible prompt to update with tests and a scene, the
Playwright changes, an offline e2e test in two browsers and docs. On localhost
it opens as fast or slower, and it's worth about a 3 once the server is remote
over HTTPS.

**Risks and principles.** A stale shell outlives a fix if the prompt is ignored,
and a remote server needs HTTPS to register one at all. It fits the thick client,
but a new place of storage needs its STATE.md row and a runtime namespace, and
the prompt must work with a keyboard and a screen reader.

**Depends on.** [#8](#8-listen-on-a-release-build-and-run-the-end-to-end-tests-against-it).

**Sources.** [web.dev on the service worker lifecycle](https://web.dev/articles/service-worker-lifecycle),
[web.dev on navigation preload](https://web.dev/blog/navigation-preload),
[MDN on using service workers](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API/Using_Service_Workers),
[Playwright on service workers](https://playwright.dev/docs/service-workers).


## Measuring as you go

Measure every change on a release build (#8, with #83's perf define), at 4x CPU
throttling, with your own library or #43's, and take the same trace before and
after. Dev builds overstate every cost and hide others.

- **Renders and the work behind them.** The measures of #79 name each render's
  route and changed keys, and #82 counts the renders per action. Use them for
  #1, #3, #4, #9, #12, #16, #26, #30, #44, #52, #76, #80 and #81, e.g. a playing
  Library page should show one render a second after #3, and a near-free one
  after #9.
- **Freezes and slow clicks.** The log of #87 splits slow interactions into
  their phases, and in Chromium names the long frames with no input, such as a
  refresh landing. Use it for #2, #7, #13, #22, #45 and #49, and to find out in
  which browser you see the seconds.
- **Indexing.** The browser bench of #33 times the page's share of a
  subscription and of a refresh in podcast-clj, at 4x, for #7, #13, #41, #42 and
  #99.
- **Startup.** The marks of #84 split the blank into scripts, the library read,
  the server and the colours, and #95 splits the scripts into compiling and
  evaluating. Use them for #10, #11, #36, #53, #56, #63, #85, #86, #89 and #96.
- **The network.** The Server-Timing of #65 shows the proxy's queue and the
  thumbnails' jobs in DevTools, for #18, #32 and #47, and DevTools' Queueing and
  Stalled columns show the browser's own wait.
- **Playback.** The marks of #71 time a Play to 'playing' for #29, #48, #49 and
  #98, and the tally of #55 counts what a hidden page does, for #3 and #44.
- **Scrolling.** The phone audit of #90 checks #40, #73, #91 and #92.
- **Memory.** The heap protocol of #93 decides #46, #70 and #94.
- **Bundles.** The report of #64 checks #68, #69 and #96, and keeps their gains.
- **Regressions.** The budgets of #88 keep the freezes from coming back, and #57
  keeps the motion items honest under reduced motion.


## Considered and dropped

- **Paint Play next and Play last first, and play an outside episode before
  adding it.** Adding an outside episode builds documents for one item, not the
  whole feed, and Play already calls `play()` in the click, so what's left is a
  few renders.
- **Serve the app over HTTP/2 on localhost.** It needs ALPN and a trusted
  certificate on every machine and in CI, and every /proxy request still queues
  for the server's four permits; #18 frees the connections for far less.
- **Start the directory searches when typing pauses.** Each directory's list
  already renders as it answers, a guess helps only if the paused text is final,
  and it sends unsubmitted keystrokes to third parties and spends Apple's rate
  limit.
- **Name the memoized views and log their slow recomputations in dev.** The
  render measures of #79, with their changed keys, answer the same question
  without changing a dozen call sites.
- **Reserve room for images in show notes that come without dimensions.** Most
  images in the corpus's notes have dimensions, and a CSS rule would turn ATP's
  inline GIFs into full-width blocks.
- **Subset Charis to Latin under its own name.** The fonts download once per
  device and then revalidate with a 304, which #21's immutable caching removes,
  and a subset is a Modified Version under the OFL.
- **Split Settings and OPML into a lazy module, if the build report says so.**
  The manifest already says no: only Settings' views are page-only, a few kB in
  release, on a page in the main navigation.
- **Wait only for the first screen's cached colours before the first render.**
  Each artwork left out would cost a render and a full listing of the cache
  after the first render; #36 fixes the real cost.
- **Render without waiting for /api/server.** On localhost it's a loopback round
  trip, almost never slower than the library read, and podrolls and searches
  would be cached without the Podcast Index's results; #53 keeps the preload.
- **Load Settings and OPML's import and export as a shadow.lazy module.** The
  loader costs about what it moves, Settings would gain a wait and a way to fail
  offline, and any split should follow #95's coverage instead.
