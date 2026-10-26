# Taking the search index off the page's start

*2026-10-26, for Simon Gray. A plan for #99 of
[the smoothness report](smooth-frontend.md#99-keep-the-search-index-in-the-worker-and-ask-it-for-picks).*

Item #99 proposed moving the whole search index into the web worker and
asking it for the picks. The measurements below show that the picks
themselves are cheap. The index costs the page in three other places:
reading it before the first render, adding a show to it, and encoding its
segments when the library is saved. This plan moves those three into the
worker and leaves the index on the page, in three stages.


## Where it stands

Stages 1 and 2 are built, and stage 3 isn't started. With the benchmark's
library, `bench.mjs open` shows Home after 0.66–0.75 s rather than
1.85–1.86 s, and the longest task at the start takes 0.22–0.23 s rather
than about 1.45 s. The index arrives after 1.97–2.14 s, when the picks are
found, and making it from the worker's parts takes the page a task of
60–80 ms.

So the picks come a little later than before, when they came with Home at
about 1.9 s, since the worker starts to decode only once the page has
read the rest. The worker could read the index's manifest itself at the
start, and decode the index while the page reads the feeds.

Two things differ from the plan below:

- **The decoding runs outside the tab's turns.** A tab reads and writes
  its library one turn at a time. Loading the index in a turn of its own
  held up the saves after it, e.g. that of a Play just before a reload, so
  the worker decodes the index outside the turns. Only joining the index
  to the library takes a turn, which is short.
- **The worker starts during the read only when there's a saved
  library,** since one that runs unused takes a heap of its own.


## What the index cost before

These numbers are from the dev build in headless Chrome, with the
benchmark's library of 53 shows (27,625 documents in the index), two runs
each. A time is what a CPU profile spent under the functions named,
sampled every 250 µs. The heap is what the page held after three garbage
collections, 12 s after Home appeared.

| Where | Now | Of that, the index |
| --- | --- | --- |
| The start's blocking task | 1.43–1.53 s | 1.14–1.20 s to read it. The feeds take 0.16–0.17 s. |
| Home appears | 1.99–2.15 s | Without an index, Home appears after 0.69–0.71 s, and the longest task is 0.21–0.24 s. |
| Home's picks | 29–33 ms | 22–25 ms is counting each show's episodes for the cap on common words. |
| Subscribing to the biggest show | two tasks of 0.46–0.53 s | 0.36 s to add it to the index, merges included, and 0.37 s to encode its segment at the save |
| The save after an import of the 53 shows | one task of 3.7 s | 3.2 s to encode the segments |
| The page's heap | 243 MB | about 51 MB, against 192 MB without an index |

The index has six segments of 2,421–6,343 documents each, with 0.36–1.37 MB
of postings. Reading one segment at a time would still leave tasks of
roughly 0.2–0.3 s.

The script that measured these isn't in the repository. It's a variant of
`bench.mjs` that profiles Home's start and a subscription, and can set
`:search? false` in the library's config before init. Stage 1 adds those
modes to `bench.mjs`, to measure each stage before and after.


## Why not the index in the worker

Item #99's own stage 2 would have the worker own the index, with the page's
library made without one.

- **It moves the memory rather than saving it.** The worker would hold the
  51 MB. Each tab has its own worker, and Chrome for Android has no
  SharedWorker for the tabs to share one.
- **The worker would need the feeds too.** The picks' criteria (unplayed,
  not queued, clean) read the profile and each episode's feed. The worker
  would need a copy of the feeds or a table of their facts kept in step
  with the page.
- **The picks and autoplay become asynchronous** for 30 ms of work, and
  autoplay has to ask the worker at the end of an episode, also in a hidden
  tab on a phone.
- **The index needs a place of its own.** Its manifest is in the library's
  own document, so a page that saves without an index would drop it.

Nothing measured needs that, so it waits until a phone shows that the
51 MB matters (#90's runs).


## Stage 1: show the page before the index is read

podcast-clj gets an option of `read!` that leaves the index in its
documents, and a `load-index!` that reads it afterwards, as `load!` does
for the feeds. While it's unloaded, the library holds the saved manifest in
place of the index:

- `write!` saves that manifest as it is and keeps its segments, as it does
  an unloaded feed's document.
- A change that would index a feed again (a subscription, a refresh or an
  unsubscription) notes the feed instead. `load-index!` indexes the noted
  feeds, and `reconciled` drops the shows that went meanwhile.
- A catch-up with another tab's write takes that tab's manifest.
- `recommend` returns nil, as it does for a library without an index.

The app's `open!` renders once the feeds are read, and loads the index
straight after. The picks aren't found while the index is unloaded, so
their sections keep saying "Looking for episodes…" until it arrives, and
then the picks are found because the index is part of their key. An
episode that ends before the index loads, in the first two seconds or so,
doesn't autoplay.

**Gain.** Home appears after about 0.7 s rather than 2.0 s in this library.

**Catch.** On its own, this stage moves the 1.15 s task to just after Home
appears, when the listener is likely to click. Stage 2 removes it.

**Risk.** A path that misses the unloaded state would save the library
without the manifest. The index would then be built again from the feeds
at the next start, and its old segments would stay in the store. That's
slow and untidy, but the index is derived data, so none of the listener's
data is lost. The tests cover each of those paths, on the JVM and in the
browser.

**Effort.** About 14 h: podcast-clj and its tests, the app's open, a check
of the picks' sections, an e2e test that Home shows before the index and
that a subscription during the load is indexed, and the bench.


## Stage 2: decode the index in the worker

`load-index!` takes a function that returns the index's segments, by
default fetching and decoding them on the page. The app's function asks
the worker instead. The worker already uses the library's store for a
refresh, so it fetches the segments from IndexedDB itself and decodes each
one into its arrays. It transfers their buffers to the page and sends the
ids and the stored documents' EDN text with them. The page then makes the
segments and the index from those parts.

drop-in-search splits reading a segment into decoding its parts and making
a segment from them. A test checks that the two together equal what
`ciff/read-files` returns.

**Gain.** The 1.15 s task becomes the time to make the segments from their
parts. That's unmeasured, but it's mostly the 27,625 ids made into vectors.

**Cost.** The worker starts with the page, which takes it a quarter of a
second, rather than when a request is near. The refresh at idle needs it
soon after anyway.

**Effort.** About 12 h.


## Stage 3: build and encode segments in the worker

For a subscription and a refresh's changed feeds, the worker already builds
the search documents (#7). It would also build their segment and encode it
as CIFF, and transfer both.

- drop-in-search adds a built segment to an index, numbering its fields as
  the index does, and lets `ciff/plan` take bytes that are already encoded
  for a segment.
- podcast-clj's `reindexed` takes a built segment, and `write!` takes a
  function for a segment's bytes, which the app answers from the worker.
- Merges stay on the page, about 50 ms here. The worker encodes a merged
  segment from a copy of its arrays.

**Gain.** About 0.73 s of the 0.95 s that subscribing to a big show costs
the page, and most of the 3.7 s save after an import. A refresh's parts
gain too, unmeasured.

**Effort.** About 18 h.


## Decisions for you

1. **Stage 1 reverses init's choice for the index.** Its docstring says the
   page waits for the library because it can depend on all of it. The index
   is derived data, and the picks already show their sections before
   they're found, so I recommend it.
2. **Whether stage 1 may land alone.** I recommend landing stages 1 and 2
   together, so that no build moves the freeze to just after Home
   appears.
3. **When stage 3 comes.** It helps subscriptions and imports, not the
   start, so it can wait for a measurement of a refresh in your own
   library.

A small fix stands apart from the stages. Counting each show's episodes for
the cap on common words takes most of Home's 30 ms of picks, and it could
be worked out once per index rather than at every search for picks.
