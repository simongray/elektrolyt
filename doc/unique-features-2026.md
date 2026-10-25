# Unique features for Elektrolyt, October 2026

*Research of 25 October 2026 for Simon Gray. The same report is the Claude Doc
[Unique features for Elektrolyt](https://claude.ai/artifact/Cn4kWF7SbamjyyNNqY6Dzu).*

## Summary

Eighteen features survived the judging, out of 105 ideas from eight lenses merged into 48. Every one rests on a function that exists in podcast-clj, play-nice, like-minded, mind-meld, colour-coded or ical-clj, and fifteen of them have a first slice that is an S: a view and an effect over that function. Three themes hold them: the transcript as the page it looks like, the library as a bookcase, and the listener's own data, devices and desk.

| # | Feature | What gives it | Effort |
| --- | --- | --- | --- |
| 1 | Read along, with a live caption line | podcast-clj's transcript paragraphs and `at`; play-nice's text tracks and wake lock | M, first slice S |
| 2 | Who speaks: the transcript set like a play | `transcript/turns`; colour-coded's tones and colour-vision check | M, first slice S |
| 3 | Highlights, notes in the margin, and the commonplace book | `profile.marks`; the transcript's runs; play-nice's keymap | M, first slice S |
| 4 | Quote a passage, share the moment | paragraph times, `directory.links/at-time`, media fragments | M, first slice S |
| 5 | Where did they say that? Search the spoken words of your library | drop-in-search's index, `transcript/matches` | M, first slice S |
| 6 | Print the episode as a booklet | the page's own HTML; the transcript writers | S |
| 7 | The Library as a shelf, each show in its own colour | colour-coded's palette, distance and vision simulation | M, first slice S |
| 8 | Your place in the story | the serial order, seasons and `enqueue-all` | S |
| 9 | The stretches you heard | play-nice's `:played` ranges, already in the store | M, first slice S |
| 10 | I have 35 minutes | `library/episodes`, `listed`'s remaining time, chapter lengths | M, first slice S |
| 11 | A calendar of expected episodes | `feed.schedule` and ical-clj | M, first slice S |
| 12 | A page for every person | `resolve/people`, the people field of the index | M, first slice S |
| 13 | An audiobook as a show | `feed.local`, media tags by byte ranges, `subscribe-local` | M, first slice S |
| 14 | Sync through a folder or server you already have | like-minded's folder, WebDAV and encrypted stores; mind-meld | M, first slice S |
| 15 | Share into Elektrolyt, installed with a count | `feed.discovery`, a manifest, the Badging API | S |
| 16 | Keep three for the train | `downloads`, wary-fetch's streaming proxy, OPFS | L, first slice S plus a mount |
| 17 | The episode's conversation, read here | `comments/thread`, `fetch!`, `tree`, `hiccup` | S |
| 18 | A floating reading window | a new Document Picture-in-Picture part | L |

The landscape research says why these matter: sync failures and download failures are the two largest complaints about podcast apps; listeners have asked Pocket Casts for notes and export since 2023 and been refused; and the features that read a transcript, follow a person or search your own library's words are missing from every app, not because they are hard but because the apps have no library like podcast-clj behind them.

## How to read this

A feature made the list when all four hold: one of your libraries does the
hard part already, or would with a little work; a listener would want it, or
it extends a podcast app naturally; few podcast apps have it; and a listener
would remember it. Features that every app has, such as a sleep timer, appear
only where the libraries allow a twist that none has.

The ideas come from two rounds of research on the night of 24 to 25 October
2026:

1. **The inventory.** Nine readers went through each library area against
   its code and the app's use of it: podcast-clj's comments, payments,
   formats, profile, feed model and directories, sync and server; play-nice;
   colour-coded with mind-meld; and the IndieWeb libraries. A tenth read the
   app itself. Four researchers mapped the landscape: the mainstream apps,
   the Podcasting 2.0 apps, research and experiments since 2019, and what the
   web platform allows.
2. **The ideas.** Eight ideators each took a lens (reading, ownership,
   community, time, colour, the power listener, sharing, accessibility) over
   the inventory. Their ideas were merged into one list, and each surviving
   idea was judged twice: for feasibility, by reading the library functions
   it names, and for rarity and listener value, by checking the apps that
   might have it. The finalists got a closer reading of the code, and
   a critic looked for what the inventory offered that no idea used. A
   second critic tested the finalists against the app's principles the next
   morning; its findings are in "What the sceptic said".

The effort grades are the ones the judges used:

| Grade | Meaning |
| --- | --- |
| S | A view and an effect in the app over a library function that exists |
| M | New model or persistence in the app, or a small addition to a library |
| L | A new library part, a server part or new protocol work |

Each feature's section says what the listener gets, why it is rare, which
functions give it, what the app and the libraries still need, and where the
research came from. Line numbers refer to the checkouts beside the app on
that night and will drift.

## What the app has, and what the libraries leave unused

Of the 260 capabilities the readers found in the libraries, the app uses 44
fully and 62 in part, and leaves 125 untouched. The app today is a complete
listener: subscriptions with a destination for new episodes, a queue with
undo, an inbox, OPML in and out, a refresh in a worker, search across four
directories and drpodcast.nu, Show and Episode pages with chapters and a
transcript, a player with speed, skips, chapters and the lock screen, picks,
Home, Settings, and colours from the artwork. What it lacks is everything a
listener would call a feature of their own: nothing is starred, bookmarked,
clipped, filtered, downloaded, synced, shared or commented on, and the
transcript is read but not searched, followed or spoken.

The table names the unused parts by area; the sections below pick the ones
worth building.

| Area | Unused | Partly used |
| --- | --- | --- |
| podcast-clj comments and Nostr | Threads over ActivityPub, Bluesky and Nostr in one shape; posting, replying and reacting on Nostr with a signer; what changed since the last reading; threads about any episode by its NIP-73 id | The proxy sender, which needs comments' own send function |
| podcast-clj payments | Who is paid at a moment and the time splits; paying through a WebLN extension or Nostr Wallet Connect; boostagrams; the record of payments; the health of a value block | Funding links |
| podcast-clj formats | The transcript segment at a time; search within a transcript; word timing; SRT, VTT and JSON writers; a show from local files; integrity checks; writing ID3 tags | Chapters from show notes; chapter images and links; transcript paragraphs; media files by byte ranges; OPML folders |
| The profile | Stars, bookmarks, clips, ratings; playlists and list feeds; hidden episodes; notifications of new episodes; followed OPML lists; download rules; live items | Saved filters; per-show settings; folders and tags; listening counts; the device registry |
| The feed model | socialInteract, soundbites, trailers, chat, locked, live items; the diff between two fetches; the next episode from the update frequency; validation and file checks; .ics calendars | Medium and list feeds; AI disclosure; images; alternate enclosures; categories |
| Directories, updates and sync | Charts; episodes by person and recent; identifying a show across directories; Podping and WebSub; sync through a folder, WebDAV or an encrypted store; the gpodder backend and server; downloads through the server | Links to other apps; the Podcast Index's other calls |
| play-nice | Chapters as a text track; captions over audio with the FCC's styles; choosing the file the browser plays best; wake lock; fullscreen; Picture-in-Picture; AirPlay and Chromecast; autoplay; the embed attribute | Media state, sources, rate, the session, moment links, the keyboard |
| colour-coded and mind-meld | Palettes with shares; whole themes; tones and target contrast; harmonies; colour-vision-deficiency simulation; colour differences and mixing; the device registry; changes since a state; stamps as a timeline | Seeds and thumbnails; counts, sets, ordered lists and field writes |
| The IndieWeb libraries and the rest | Micropub posting and an endpoint; IndieAuth sign-in; Webmentions; microformats; snippets; WebDAV client and server; streaming downloads with resume | Library search; language tags; show notes as text; probing a file |

## Reading: the book you read while you listen

Six features make the transcript the page it looks like. They rest on podcast-clj's transcript functions, which parse SRT, WebVTT, JSON, HTML and text into segments with speakers and times, and on play-nice's text tracks. Together they would make Elektrolyt the one podcast app a reader, a student or a Deaf listener would choose.

### 1. Read along, with a live caption line

**M; the first slice is an S.** The publisher's own transcript follows the voice. On the Episode page the paragraph being spoken carries a hairline in the show's colour and stays in view; what you have heard is in ink and what lies ahead in grey, and the time before each paragraph plays from there. A CC key beside the speed puts one line of what is being said under the title in the player bar on every page, the speaker in small caps. A Playback setting keeps the phone's screen on while the transcript is open, and only then.

Highlight-and-tap is common now, but only for the apps' own generated transcripts. Apple and Spotify highlight their own; Pocket Casts syncs only its paid generated ones since June 2026, while feed transcripts there "do not highlight or support tapping a line to jump" ([Pocket Casts](https://support.pocketcasts.com/knowledge-base/episode-transcripts/)). Castamatic 13 and Overcast 2026.04 follow the publisher's transcript on the iPhone. No app shades heard against unheard text, keeps the screen on for reading, or captions audio in its player bar; phones do that system-wide, podcast apps don't.

podcast-clj ranks the feed's transcripts, parses them and gives the paragraphs and the segment at a second (`resolve/transcript`, `transcript/paragraphs`, `transcript/at`). play-nice adds a hidden text track to the audio element and reports the active cue (`tracks/add!`, `tracks/watch!`), styles captions in the FCC's vocabulary and holds the screen (`wake-lock/hold-while-playing!`). The app already keeps the position and the element's played ranges in the store.

- *First slice:* the mark on the unfolded transcript as a view over state the app holds, with aria-current on the paragraph and a Details row that says "Transcript: none".
- *Then:* read the transcript at play, not only on unfold; the hidden track added in runtime and its active cue in the player's state; the caption line in the player, with the CC key in the Controls row, since the player's rows are settled; two preferences in Settings.
- *Library:* nothing, but try play-nice's tracks on an `<audio>` element in Safari and Firefox first; its tests run on a muted video.

Mind that dynamically inserted ads drift the cues, so the fold needs an honest note; that word timing exists only in WebVTT with inline timestamps, so the paragraph is the unit; and that 17% of feeds carry a transcript at all (29% of popular ones). Cite the W3C's [transcript guidance](https://www.w3.org/WAI/media/av/transcripts/), Dingman, Tigwell and Shinohara's [ASSETS '21 study](https://dl.acm.org/doi/10.1145/3441852.3476523) of Deaf and hard-of-hearing listeners, and the [Screen Wake Lock API](https://www.w3.org/TR/screen-wake-lock/).

### 2. Who speaks: the transcript set like a play

**M; the first slice is an S.** Each speaker's name stands over their turn as a small-caps running head, the time in the margin, bracketed sound cues in lighter ink, the transcriber's italics kept. At the head, when two or more speakers are named, a cast line: "Speaking: Adam Curry 61% · Dave Jones 37%", each name a button to that speaker's next turn. Where the feed credits its people, the guest's picture and role join the line.

Speaker names appear as plain labels in Apple Podcasts (for a creator's own VTT only), Metacast and Snipd. Talk-time shares and speakers in colour exist only in meeting tools such as [Avoma](https://help.avoma.com/speaker-percentages-and-monologues). The one transcript a publisher typeset for Deaf readers was Vox's hand-made one for a single series in 2021. Nothing ships "take me to the guest's next answer".

podcast-clj reads speakers from WebVTT voice spans, "Name:" prefixes, HTML cite elements and JSON, merges turns (`transcript/turns`) and paragraphs; colour-coded keeps the speakers' tones a clear step apart (`tone`, `vision`, `target`). Reading the code found a bug to fix at once: the speaker prefix matches only ASCII letters, so "Åse:" is never a speaker.

- *First slice:* the typesetting for every transcript as a view over the document the fold already reads, the cast line and next-turn buttons when timed turns exist, and a `transcript/sound-cue?` predicate in podcast-clj.
- *Then:* tones from the show's colour, Print and Copy, and the M in podcast-clj that keeps a cue's formatting beside its plain body through turns and paragraphs.

Shares need end times, so HTML and plain-text transcripts get names without percentages. Cite the WAI guidance above, the [WebVTT](https://www.w3.org/TR/webvtt1/) voice and cue-text spans, the [BBC subtitle guidelines](https://www.bbc.co.uk/accessibility/forproducts/guides/subtitles/) on sound in brackets, and WCAG 2.2 on language of parts and use of colour.

### 3. Highlights, notes in the margin, and the commonplace book

**M; the first slice is an S.** Press one key while the player has the focus and the moment is kept in your profile as a tick on the seek bar, labelled by the chapter. On the Episode page, run a highlighter over the transcript and the words take a pale wash of the show's colour, with a Play that plays those seconds and stops; type beside them and the note stands in the margin, a size smaller, a hairline joining it to the words. A Marks page under Library gathers every highlight, note and star across your shows, newest first, each playing from its second, and exports them as Markdown with the links.

Pocket Casts has bookmarks, but "you cannot add notes to your bookmarks yet" and export "is not yet supported"; its listeners have asked for notes, the transcript around a bookmark and export to Readwise and Notion since April 2023, and in May 2025 staff said there were [no immediate plans](https://forums.pocketcasts.com/forums/topic/bookmarks-notes-and-sharing/). Metacast bookmarks a quote on the transcript; Snipd exports snips to Obsidian on a paid tier. Nobody draws the highlight back on the words or sets a note in the margin.

podcast-clj's `profile.marks` keeps bookmarks at a second, clips with a duration and stars, synced and tombstoned; `transcript/paragraphs` gives the runs a selection maps to; play-nice's `controls/listen!` takes a keymap for the key.

- *First slice:* "Mark this moment": the key and a Mark button write a bookmark with the chapter's title, ticks on the seek bar, and a Marks page that lists them.
- *Then:* a `:note` on bookmarks and clips in podcast-clj (a few lines, already on its needs list), the highlighter over the transcript's spans, the clip's range in the player with the reporter silenced while it plays, and the Markdown export.

Marks are keyed by whole second, so two highlights starting in the same second replace each other; most transcripts time cues, not words, so a highlight snaps to cue edges. Cite Pocket Casts' [bookmarks](https://support.pocketcasts.com/knowledge-base/bookmarks) and that forum thread, [Metacast](https://apps.apple.com/app/metacast/id6462012536) and Snipd's [Obsidian export](https://forum.obsidian.md/t/snipd-plugin-to-sync-your-podcast-highlights-to-obsidian-with-transcripts-ai-notes-summary-metadata/26499).

### 4. Quote a passage, share the moment

**M; the first slice is an S.** Select words in the transcript and a small bar rises: Copy, Copy as Markdown, Share. The quotation carries the words, the speaker, the episode and the show, the minute, a link into the app at that second, and a link into the publisher's own file ending in `#t=842,884`, which plays exactly those seconds in any browser with no app at either end. From the player, Share gives the moment the same ways, plus a QR code to hand it to your phone and the episode in one other app you chose.

Apple copies up to 200 words with a link back into Apple Podcasts, no speaker, no second ([Apple](https://podcasters.apple.com/support/5316-transcripts-on-apple-podcasts)). Pocket Casts shares pca.st links; Snipd exports to notes apps, paid and English only. AntennaPod alone writes the file's URL with `#t=`. Every mainstream share is a route back into one app; a thick client with nothing to acquire can point outward.

podcast-clj gives the paragraphs with their times, the links to other apps (`directory.links/links`, `at-time`) and the page's own route with `?t=`; play-nice parses the fragments. The views already compute the `:links` the app throws away.

- *First slice:* the selection bar and "Quote this paragraph" after each paragraph for touch and keyboard; a selection-to-quotation function, a clipboard effect and a share effect, no profile write.
- *Then:* the share dialog in the player with the `s` key, the QR code (a new dependency), and a small addition to podcast-clj so each app's link can carry a start time.

The app's server listens on localhost, so the link into the app opens nothing on a phone until there is a public host; the file link is the one that travels. The `#t=` fragment stays in the browser, so the host sees an ordinary request for the file and counts a download. A private feed's enclosure URL carries the subscriber's token, so Copy, Share and the QR must refuse or strip it there. Cite [Media Fragments URI](https://www.w3.org/TR/media-frags/), the [Web Share API](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/share) and Apple's attribution rule.

### 5. Where did they say that? Search the spoken words of your library

**M; the first slice is an S.** On the Search page, a word finds the episodes you follow where it was said, each as a quotation with the speaker and the minute, and opens the Episode page scrolled to the passage with "Play from 41:12". Offline, with no account, over the transcripts in IndexedDB.

Apple, Pocket Casts, Snipd and Podwise search one episode's transcript at a time; Overcast's library-wide search was still a roadmap item at its April 2026 launch. Catalogue engines such as [Audioscrape](https://www.audioscrape.com/docs/features/search) search 170,000 hours of other people's audio; no app found searches the spoken words of your own library, though Metacast advertises a search across podcasts and Podwise's scope is unstated, so check both before calling it unseen.

drop-in-search already indexes shows and episodes in the worker, `transcript/matches` finds the passages of a transcript for a query with the second of the match, and the search documents have a `:transcript` field that nothing fills yet.

- *First slice:* an "In your library" section on Search over the transcripts this tab has already read (the playing episode and the twenty read last), headed with how many it searches; no fetching, no cache, no library change.
- *Then, the M:* a store of transcripts in IndexedDB, a fetch policy in the worker that respects the proxy's four bodies at a time, and the podcast-clj change that threads a transcript function through the document building and reindexing, with `:max-field-terms` on the transcript field and transcript snippets.

The first slice proves the words and the link, not the feature: it searches only the transcripts this tab has read and forgets them at a reload, while the lead describes the M, which the deep dive calls near L. Index conservatively: drop-in-search measured 317 MB of heap for 152,000 documents and 23 million words in Node, and an hour's transcript is ten thousand words. Cite the [TREC Podcasts Track](https://trecpodcasts.github.io/), Park et al. at [IMX 2024](https://dl.acm.org/doi/10.1145/3639701.3656324) on listeners needing to jump to segments, and Audioscrape as the catalogue counterpart.

### 6. Print the episode as a booklet

**S.** Press Print and the Episode page prints as paper: the rail, the binding and the buttons gone, the title and show on the first page with the artwork in grey, the chapters as a contents with their minutes and lengths, the notes with their indents, and the transcript in Charis with the speakers in small caps and the minute of each paragraph in the margin, the show's colour as the one spot colour. Each link is followed by its address; the footer credits the show. Beside Print, Save transcript offers the feed's own file, or text or Markdown with speakers and minutes.

No podcast app prints an episode or lays it out as a document. Apple caps copying at 200 words and lets only creators download; Pocket Casts has no export; the nearest things are Metacast's paid transcript export and CastKeeper's notes to PDF. Teachers, students, researchers and Deaf readers want it and have nowhere to get it.

The Episode page is already semantic HTML, and podcast-clj's `transcript/paragraphs`, `transcript/text` and the SRT, VTT and JSON writers exist.

- *First slice:* an `@media print` layer and a Print effect that unfolds the chapters and the transcript, waits for the transcript to read, then calls `window.print()`.
- *Then:* a dialog choosing the sections with a page estimate, running heads in margin boxes where the engine has them, and Save transcript with the OPML export's Blob pattern.

The transcript and the notes are the publisher's: keep to the browser's own print and download, credit the show, never host the PDF. Firefox has no `@page` margin boxes, so the running heads are progressive. Cite [CSS Paged Media](https://www.w3.org/TR/css-page-3/), the WAI transcript guidance, and the [podcast:transcript](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/transcript.md) formats.

## The library as a book

Seven features give the listener's own library the shape of a bookcase: a shelf of spines, a ribbon in a serial, the pages you have read, what fits the time you have, when the next episode is due, the people in it, and your own recordings as a book.

### 7. The Library as a shelf, each show in its own colour

**M; the first slice is an S.** A third Library layout, Shelf, beside Grid and List: every subscription stands on one hairline as a flat book spine in a colour of its own, the title reading down it in Charis small caps, two or three thin stripes of the artwork's other colours at their shares, the unplayed count at the foot. A long-running show is a thick spine, a six-part series a thin one, and none thinner than a fingertip. Each show's colour is chosen once, a clear step from its neighbours' and from what a colour-blind eye would confuse, and kept, so the listener learns it as they learn a spine.

No podcast app presents subscriptions as spines or gives shows a stable, distinct colour; the only colour-coding found is Pocket Casts' folder colour, picked by hand. The prior art is books: Delicious Library's shelves (2004 to 2024), the iBooks shelf flattened in 2013, and Open Library's [virtual shelf prototype](http://openlibrary.org/docs/virtualshelf), whose spines took their thickness from page counts and their colour from ten at random. It is the one view a listener screenshots.

colour-coded gives the artwork's palette with shares (`image/palette`), the distance between colours (`difference/delta-e`), tones at fixed lightness and the simulation of a protan, deutan or tritan eye (`vision/simulate`); the app already caches a seed per artwork and the Library already counts episodes and unplayed per show.

- *First slice:* the Shelf layout over the seed the app already caches: a spine variant of the show item, width from the episode count on a log scale with a 44px floor, the numeral, one CSS block; no stripes, no stored colour.
- *Then:* cache the palette beside the seed; a pure assignment of one colour per show in the listener's order, a ΔEOK of at least 0.1 from every colour already assigned (and, with a colour-vision preference, after simulation); the colour written to the show's settings as the app's first per-show write. The greedy assignment can live in the app until a second app wants it in colour-coded.

Vertical small caps read slowly and touch has no hover, so the title the right way up is a tap away; sixty spines at the floor are 2,640 px, so the shelf scrolls sideways below 480 px. The list keeps its role and every spine its linked heading, so a screen reader hears the grid. Two of your rules push back: the Library is a general page you keep neutral, and a colour turned along the wheel is no longer the artwork's, so turn only when two seeds collide and say so; and on touch a tap should reveal the title before it leaves the page. Cite the Open Library prototype, [Machado, Oliveira and Fernandes](https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html) on simulating colour vision deficiency, and WCAG 2.2 on use of colour.

### 8. Your place in the story

**S.** A serial show's page reads like a book with a ribbon in it. Under the byline a thin rule in the show's colour runs a third of the way across, and beside it in small caps: "Episode 7 of 24 · 9 h 12 m left". The first row of Episodes is "Next · 7. The second letter · 41 min" with Play; the six heard are in lighter ink. A button queues the rest of the season in one press, with Undo. A switch hides the titles of later episodes against spoilers, and another, "Hear in order", gives any show the ribbon.

Apple lists a serial from the first episode with seasons and hides played ones, but shows no progress, no time left and no spoiler fold ([Apple](https://podcasters.apple.com/2952-ios-154-whats-new-for-apple-podcasts)); Aurallisten, an open-source Android app, comes closest with listening order and an unplayed counter. Libby and Audible show "x h left" for a book; no podcast app does. Hiding titles against spoilers exists only on TV players' wish lists.

podcast-clj already orders a serial's episodes season by season (`resolve/episodes`, `resolve/serial?`, `resolve/numbering`), knows the medium (audiobook, course), honours a per-show order, and queues a list under one stamp (`profile.queue/enqueue-all`).

- *First slice:* the ribbon on shows that are serial by their feed: a pure progress function, the sentence and the rule under the byline in place of the Details' "Serial, from the first episode", and the pinned Next row.
- *Then:* the season button, the spoiler fold and "Hear in order" as the app's first per-show setting writes.

Only 1.7% of feeds call themselves serial and some mis-tag, so "Hear in order" is how most listeners reach the ribbon; durations are missing in some feeds, so the sentence says "at least" or falls back to "17 episodes left". Cite Apple's [serial type](https://help.apple.com/itc/podcasts_connect/#/itcb54353390), [podcast:medium](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/medium.md) and Libby's [time left](https://help.libbyapp.com/en-us/6047.htm).

### 9. The stretches you heard

**M; the first slice is an S.** The seek bar shades every part of the episode the listener has actually heard, as faint pencil hatching under the chapter notches, from the element's own played ranges rather than one resume point. It appears only once a seek leaves a gap, and a "Next unheard" button jumps to the first gap. The Episode page's facts read "41 min heard of 2 h 05 m", and each chapter carries a dot: filled, half or empty.

No podcast app shows which parts of an episode were heard; they keep one position and a played flag. YouTube's red bar is one position per video. The browser has exposed the heard ranges as [HTMLMediaElement.played](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/played) since 2015, play-nice reads them into `:played`, and the app already has them in its store.

- *First slice:* round `:played` to whole seconds in the player's state (which also stops the store changing at every tick, as its docstring promises), draw the heard layer under the chapter marks, add "not yet heard" to the slider's spoken value, and the "Next unheard" button. Two pure range helpers start in the app and move to play-nice at its next release.
- *Then, the M:* persist merged, capped ranges per episode in an app-owned IndexedDB document, since the element's ranges vanish at a reload or a switch of episode; until then the bar opens plain rather than wrong.

The rarity judge wanted persistence before shipping; the first slice avoids a wrong morning bar by showing nothing until a seek leaves a gap, and the sceptic still calls that a thin first step. Cite the HTML Standard's [played attribute](https://html.spec.whatwg.org/multipage/media.html#dom-media-played) and play-nice's own note on `:played` as the seconds actually heard.

### 10. I have 35 minutes

**M; the first slice is an S.** On the Queue page, under the Up next summary, a short line reads "I have" with 15 · 30 · 45 · 60 and a field. Press 30 and a section "Fits in 30 minutes" lists, in rows like the queue's own, the interview you paused with 28 minutes left, a 24-minute unplayed episode from the inbox, and "Chapter 4, The verdict · 19 min" of a two-hour episode with the chapter's own image. Play the chapter and the player says "Stops at the end of the chapter", and does.

Pocket Casts' Smart Playlists filter by length, as standing rules over whole episodes; [CueSesh](https://cuesesh.com/) (iOS, 2026) is built on the question but fills the time with whole episodes from its own catalogue. Counting time left rather than length, and offering one chapter of a long episode as the thing that fits, are not shipped anywhere.

podcast-clj's `library/episodes` selects by status and duration and orders by shortest, `library/listed` gives each row its remaining time, and `chapters/with-end-times` gives chapters their lengths.

- *First slice:* the presets and the section with started and unplayed episodes, as rows with Play and Play next, the budget in the tab's store.
- *Then:* chapter rows, the stop at a chapter's end as player policy on the tick (the library's sleep timer counts whole episodes, so it can't be reused as the judges hoped), the number field, the budget remembered per device, and a `:remaining` criterion in podcast-clj so a saved filter can say "under 30 minutes left".

Cite Harrison et al. at [Frontiers in Psychology 2023](https://pmc.ncbi.nlm.nih.gov/articles/PMC9885971/) on listening being bound to context, and Groot Kormelink et al. in [Journalism Studies 2025](https://www.tandfonline.com/doi/abs/10.1080/1461670X.2025.2487857) on news listening tied to daily moments.

### 11. A calendar of expected episodes

**M; the first slice is an S.** Every show says when its next episode is expected: a Details row "Expected Thursday 29 October", the schedule line continuing "usually Thursdays, early morning", and a rule the width of the column with one tick per episode of the last year in the show's colour, so a summer break is bare paper and a daily run a comb. A show that stopped says "On a break since 2 July" and promises nothing. Home lists what is expected today; the Library sorts by what comes next; a quiet download in Settings puts the expectations into your own calendar as .ics.

Apple has carried a release-frequency label since iOS 14.5, inferred and [mocked for being wrong](https://9to5mac.com/2021/04/15/ios-14-5-apple-podcasts-release-schedule/); Pocket Casts predicts a next date as one line; TrueFans listed the idea in 2026. No app offers a calendar, a due-today list, a library ordered by what comes next, or a drawn rhythm.

podcast-clj's `feed.schedule` gives the next occurrence of a feed's stated update frequency (`next-episode`, through ical-clj's recurrence rules) and the cadence of its dates; ical-clj writes .ics calendars with time zones and validates them. The Show page already calls both for its schedule text.

- *First slice:* the "Expected …" row, shown only when the feed has ten dated items, isn't complete, and the date lies after the last fetch. That slice is parity with Pocket Casts' one line; the rare parts come with the regularity test.
- *Then:* about twenty lines of pure code in podcast-clj (a public prediction, a regularity test so irregular shows are never "late", a weekday-and-hour profile), the year of ticks as inline SVG with a hidden list for screen readers, "Expected today" on Home, the Library order, and the .ics builder.

Only 0.6% of feeds state a schedule, so nearly every date is a guess from the median gap and must read as an expectation; rules run on UTC days, so "every Monday" can land a day off near midnight. Cite [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545) and [podcast:updateFrequency](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/update-frequency.md).

### 12. A page for every person

**M; the first slice is an S.** Tap a host's or guest's name in the People row and get their page: the name in Charis with the picture, then "In your library" grouped as host and as guest, rows like the inbox's, newest first, and a labelled "Named in the show notes" list in lighter ink so the page is rarely empty. Later, "Elsewhere" lists their appearances on shows you don't follow, from the Podcast Index, and a bookmark follows the person: the Inbox row of a new episode then says "With Ezra Klein, whom you follow".

No mainstream app builds a person page over the listener's library from podcast:person. Two 2026 iPhone apps exist only to follow people, [Attunex](https://hunted.space/product/attunex) and [PodcastIntel](https://apps.apple.com/us/app/-/id6760278262), by text matching; Podchaser keeps credits on its own site. Podnews found that no search engine indexes the tag, though names are what people search on.

podcast-clj parses the tag with roles and pictures (`resolve/people`), indexes people as a field of the search documents the worker already builds, has a `:person` criterion, and asks the Podcast Index by person (`directory :by-person`).

- *First slice:* the route, the name as a link, one `library/search` call with a people phrase grouped by role, and the show-notes fallback over titles and descriptions.
- *Then:* the follow as a saved filter in the profile with two operations, a check of each refresh's new episodes, the inbox fact, and the Index lookup through the server's credentials.

podcast:person is in 2.8% of feeds and 1.4% of items, and DR's feeds probably carry none, so measure coverage on a real library before building "Elsewhere" and the follow. Names are strings: two spellings are two people, by the app's no-word-lists rule. Cite the [tag](https://podcasting2.org/docs/podcast-namespace/tags/person), Podnews' [Who indexes what](https://podnews.net/article/who-indexes-what) and the Index's [byperson](https://podcastindex-org.github.io/docs-api/) call.

### 13. An audiobook as a show

**M; the first slice is an S.** Point the app at a folder of MP3 or M4B files on your own disk and get a show: the parts in the order a person reads their names, the cover, author and chapters out of the files' tags, a printed cover in Charis when the files have none, and the position saved like any podcast's. Nothing is uploaded. If the folder moves, the Show page says "The files aren't on this device" and offers to pick it again.

Sideloading is a paid perk that uploads or copies: Castro through iCloud Drive, Overcast and Pocket Casts to their clouds. Podcast Addict's Virtual Podcasts and AntennaPod's local folders keep the files where they are but read no tags, artwork or chapters. The only browser player found, Bookava, re-asks for the files on every visit. A folder off your own disk handed back as a book is the thick client made visible.

podcast-clj builds the feed of a local show from what the files say about themselves (`feed.local/feed` over `format.media/read!`, which reads ID3, MP4 and Ogg tags by byte ranges from a Blob), subscribes to it (`library/subscribe-local`) and plays it through a local-file function the playback already prefers.

- *First slice:* the "Add a folder of recordings" control on the Library page, the reader of the picked files, a subscribe-local operation, and the local-file function over the picked files, for one visit.
- *Then:* the directory handle kept in IndexedDB with its permission renewed from a click (Chromium), re-pick elsewhere, a copy into IndexedDB for small folders only, and the printed cover from a hue drawn from the show's guid.

Only Chromium persists a folder; Safari and Firefox re-pick, and the iPhone picks files, not a folder. Cite the [File System Access API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API), like-minded's `doc/providers.md` on what each browser can do with a folder, and podcast:medium's audiobook value.

## Yours: your data, your devices, your desk

Five features make the app the listener's own: sync through storage they already have, a place in the phone's share sheet, episodes that play with no signal, the episode's public conversation read on the page, and a page of the book that floats above the desk.

### 14. Sync through a folder or server you already have

**M; the first slice is an S.** A Sync fieldset in Settings: name this device, then "Choose a sync folder…" and Chrome's picker opens on ~/Dropbox/Podcasts; the status line reads "This laptop writes its document to Podcasts and read Phone's. Synced just now." On an iPhone, which has no picker, "Save this device's document" and "Load the folder" do it by hand through Files. A WebDAV address and app password do the same through Nextcloud or Fastmail. "Seal the documents with a passphrase" turns on AES-256-GCM, and the line reads "Dropbox sees only ciphertext; subscriptions.opml stays readable for other apps."

Every mainstream app syncs through its own account, and sync failures are the [top complaint](https://unstar.app/blog/) in negative reviews, at 19%. The no-account apps are Apple-only CloudKit: Castamatic, CastKeeper, iCatcher, Downcast. YourPods syncs through the listener's own Nextcloud or gpodder server on the iPhone, and AntennaPod through Nextcloud's GPodder Sync app; AntennaPod's maintainers refused file sync on principle. A folder you already have, on every platform, sealed with a passphrase, with a status line in words, is unseen; on an iPhone, though, only the hand sync works, three steps per device each time.

like-minded has the folder store over a directory handle or a picked folder, the WebDAV store, the encrypting store and the store check; mind-meld merges without conflict and names the devices; podcast-clj's `library/sync!` and `library/merge` run the step, and its inspector is a working template of the whole flow.

- *First slice:* "Sync by hand, in words": a name for this device, Save this device's document, Load the folder, and the status sentence; no new persistence or scheduling, and it ships in every browser, Safari on iOS 18.4 included.
- *Then, the M:* the handle, credentials and passphrase kept in a database of their own, a scheduler from `next-sync` that never runs while playing, a merge operation safe to apply again, and the Devices list as a follow-on.
- *Library:* nothing required. If CORS blocks the page at Nextcloud or Fastmail, the proxy would need to forward writes to named hosts, which is L.

Only Chromium picks a folder, and its permission can lapse between visits, so the status line must say so: silent failure is the thing this feature exists to end. Cite Kleppmann et al., [Local-first software](https://www.inkandswitch.com/local-first/), and like-minded's `doc/providers.md` on each browser's folder support.

### 15. Share into Elektrolyt, installed with a count

**S.** Install Elektrolyt from the browser as an app in its own window, the title bar in the binding's colour. A friend sends an Apple Podcasts link in Signal; Share lists Elektrolyt beside the other apps, and the app opens on Search with the feed found and the bookmark ready. A Spotify link gets an honest line instead. In Settings, an opt-in puts the inbox count on the icon.

No web podcast player registers a share target or badges its icon, and native apps register only their own domains: a shared Apple Podcasts page fails in AntennaPod because it wants an RSS feed. The moment a web app stops being a tab is a story a listener tells.

podcast-clj's `feed.discovery/find-feeds!` already turns an app's link or a page into feeds, and the Search page already runs a `?q=` search from the address bar; the server already serves every route.

- *First slice:* `public/manifest.json` with two icons and a share target on `/search`, a pure function that reads the shared URL out of the text, and two lines in the routes that turn the shared parameters into the existing search.
- *Then:* the badge from the memoised inbox count after each refresh, as an opt-in.

The share target is Chromium only, and only for the installed app; iOS and Firefox keep the paste box. An installed app on a phone must reach the server, which listens on localhost. Cite [Web Share Target](https://developer.chrome.com/docs/capabilities/web-apis/web-share-target), the [install criteria](https://web.dev/articles/install-criteria), Safari 26's [zero installability requirements](https://webkit.org/blog/17333/webkit-features-in-safari-26-0/) and the [Badging API](https://caniuse.com/mdn-api_navigator_setappbadge).

### 16. Keep three for the train

**L; the first slice is an M with a server part.** One switch in Settings, "Keep the next 3 of my queue on this device", and the worker streams those files through the app's server into the browser's private file system; a hairline under each row's artwork grows as it downloads. On the metro with no signal, Play on the row simply plays. The Episode page's Details say "on this device, 54 MB, kept because it's next in your queue", a broken download resumes from the byte it stopped at, and This browser lists the space each show takes.

Every native app downloads, and no maintained web player does: Pocket Casts' web and desktop apps "stream episodes directly from podcast authors' servers", Apple's web app names no downloads. Download failures and storage confusion are the second-largest complaint, 16% of negative reviews. A podcast website that plays on the metro contradicts what listeners know about websites.

podcast-clj's `downloads` has the rules, the plan with a reason per file, the record of files and the resume headers; wary-fetch's streaming proxy passes Range through with a sink; the playback already prefers a local file.

- *First slice:* a "Keep on this device" button in the Episode page's Details (where a TODO already sits), streaming through `/media` with a sink into an OPFS file, the record in the library's document, and a local-file function for the player; the server mounts wary-fetch's stream handler at `/media` with an allow rule.
- *Then:* the queue rule, deletion by reason, the hairlines and the space per show, and `feed.integrity`'s check of a saved file against the publisher's hash.

The server must not become an open media relay; the allow rule is the real server work, since the app's server holds no library to check against. Background Fetch is Chromium only, so on Safari and Firefox a download lives and dies with the tab, which is why resume isn't optional. Cite the [origin private file system](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system), [Background Fetch](https://developer.chrome.com/blog/background-fetch), the [Storage Standard](https://storage.spec.whatwg.org/#persistence) and [podcast:integrity](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/integrity.md).

### 17. The episode's conversation, read here

**S.** Under the folded Chapters and Transcript, a fourth running head, Conversation: "On Bluesky, 14 replies", with a link to the post. Unfolded, the replies are nested paragraphs with the authors' names in small caps, the podcaster's own replies marked by a rule in the show's colour, content warnings folded, a deleted reply left as a placeholder so the thread still reads. When nothing comes, one plain line says why. A show that named no thread shows no section, and the listener never signs in.

podcasting2.org lists six apps for podcast:socialInteract, and most link out; AntennaPod 3.8.0 only links, Podverse reads ActivityPub only, Fountain shows Nostr to its own users. Transistor writes a Bluesky root post for every episode it posts, and James Cridland's view is that the tag "[hasn't gone anywhere](https://james.cridland.net/blog/2026/comments-for-podcasting/)" because reading it means becoming a fediverse client. podcast-clj already is one, in one shape, with no account.

`comments/thread` picks the thread the feed names, `comments/fetch!` reads it over ActivityPub, Bluesky's public AppView or Nostr relays within limits, `comments/tree` and `comments/hiccup` lay it out, and the report explains an empty thread. The only plumbing is giving the proxy sender the comments' own send function.

- *First slice:* the folded section as a view and one effect, fetched with a five-second budget, no worker, no cache.
- *Then:* a cache in the worker, and the Nostr-by-guid fallback for shows that named no thread, which the judges postponed.

Only 0.4% of items name a thread, so most pages show no section; strangers' words on the paper need the reader's own dropping of hidden, labelled and non-public posts kept, and the host's replies marked. Cite the [tag](https://podcasting2.org/docs/podcast-namespace/tags/social-interact), Cridland's piece, and Spotify's [figure](https://newsroom.spotify.com/?p=29987) that listeners who interact return four times as often.

### 18. A floating reading window

**L, since the browser part is new library work.** On a desktop in Chrome, Edge or Firefox, a glyph at the end of the transport row opens a window the size of a paperback page that floats above every other app: the same grained paper, a border in the binding's colour, the chapter's image and title, the episode's title, one paragraph of the transcript with the words said so far in ink, and play, pause and the two skips. The audio never leaves the page.

Only Spotify's web player has a floating audio miniplayer, built with the Chrome team on [Document Picture-in-Picture](https://developer.chrome.com/blog/spotify-picture-in-picture); Pocket Casts' desktop miniplayer is a scrubber and three buttons; Apple Podcasts on the Mac has none. A page of the book that follows you across the desktop, with the chapter's image and the words being spoken, is the moment a browser app beats every native one.

play-nice's Picture-in-Picture is for video and refuses audio, and its docs rule Document PiP out as a window rather than media, so this is a new part: available?, open!, styles copied once, closed on pagehide, modelled on `display/picture_in_picture.cljs`. Whether it lives in play-nice or in a small library of its own is your call.

- *First slice, once the part exists:* the window with image, titles and buttons, opened from the click's own task and closed when the player unmounts.
- *Then:* the transcript paragraph, read on open as the Episode page reads it.

Desktop Chromium 116 and Firefox 151 only; no Safari, no phone, no automatic opening on tab switch. Replicant creates nodes in the opener's document, which the window must adopt. Cite the [WICG specification](https://wicg.github.io/document-picture-in-picture/) and Chrome's [rules](https://developer.chrome.com/docs/web-platform/document-picture-in-picture).

## The longer list

These passed the judges but rank below the features above: common elsewhere, rarely met, or waiting on a server the app doesn't have yet. Each is worth a line, and several are the quiet plumbing the finalists need.

| Idea | Effort | Why it ranks lower, and what to keep |
| --- | --- | --- |
| Listens on your own website: a listen post or a quoted passage posted to your blog through Micropub, signed in as your site | L | micropub-clj and indieauth-clj build every request as data, but the POST needs a token that the GET-only proxy can't carry, and the server must be public over HTTPS. No podcast app posts over Micropub; almost no listener has an endpoint. Build the quote composer first and add "Post to your website" as an opt-in destination later. |
| Your library as files you can read: download this device's document, drop it on another device to merge, a listening.md beside the sync documents | S, diary after sync | The EDN document is readable to Clojure programmers and nobody else, and the drop-to-merge duplicates sync once sync exists. Keep the download and the drop with their plain-words report; write the diary once a sync folder exists; drop the Finder mount. |
| Follow someone's list: a curated OPML list by URL, add-only, with provenance on the Show page | M | Inoreader has done it since 2014, and there is nothing to follow yet: no critic or DR page publishes a maintained list. The model is complete in profile.opml; seed the supply yourself with a list on simon.grays.blog. |
| A playlist as a feed another app subscribes to | L | Listen Notes is the only precedent. It needs a public server, a token store and a login, and a podcastL list feed is read only by Podcasting 2.0 apps. Make the unit a named playlist with plain items and remoteItems beside them, so Apple and Pocket Casts can follow it too. |
| Recommended by your shows: the podrolls of every subscription as one ranked list | M | Podrolls are in about 1% of feeds, so a library of thirty shows yields a handful of cards. The data is already in each feed document and the linked cache; show the Home section only with three or more cards, and add "Recommended by X, which you follow" to search results. |
| In step with AntennaPod and Kasts through the gpodder API | M client, L server | RePod, PinePods and PodFetch already do it. podcast-clj's gpodder backend has never met a live server and its Basic-auth POSTs need CORS the servers may lack, so it needs a same-origin forwarding route. Only subscriptions and positions cross; the queue and stars don't. |
| AirPlay and Chromecast from the player bar | S | Parity with native apps, not a differentiator. play-nice's remote-playback is complete; verify first that Chrome offers a route for an audio element, and say that downloaded and proxied media won't cast. |
| A listening diary and the year's page | M | Podcast Addict and Overcast have statistics by day, and both count the hours speed saved. The dated diary with Resume beside unfinished episodes and a printable year are new, but the profile counts seconds per show per month, so podcast-clj needs per-day counts first. |
| The file your browser plays best, in your language | M | Alternate enclosures are in 0.6% of items, and Media Capabilities adds nothing for audio. Keep three quiet behaviours over resolve/enclosure's existing options: the listener's languages rank enclosures and transcripts, the player falls back to an alternate when the main file fails, and the Show page names the transcript languages. |
| What changed since you were here: audio replaced, notes edited, episodes removed | M | No app shows it, but most changes are noise: dynamic ad insertion rewrites enclosure URLs and lengths at every fetch. Run feed/diff in the worker, keep twenty changes per show, and mark only the episode row where confusion happens, with Feedbin's threshold and per-show switch. |
| Contents, with first lines: the chapter list as a table of contents | S | Castro, Overcast and Apple show lengths and images. Keep lengths, time left in this chapter, small images, the location link, a chapters menu in the player and an opt-in spoken chapter change; show the first sentence only when the transcript is loaded and it isn't the title. |
| Find in this episode: passages where a word is said, with ticks on the seek bar | S | Apple, Pocket Casts and Snipd search a transcript. Ship it inside the read-along section as its quality mark, with quotations, speaker and minute; don't promote the query syntax. |
| A sleep timer that ends at the chapter | S | Pocket Casts has end of chapter and Audible stops at chapter marks. The app lacks any sleep timer, and library/step already honours one; the "then stop" rule between queue rows is the one part no app has. No fade on an iPhone, where the volume is locked. |
| Smart lists and rules for each show, in one filter language | S rules, M lists | Pocket Casts, Overcast, AntennaPod and Castro cover most of it. Build the per-show Rules panel first (destination and count of new episodes, hide by type or words, skip intro and outro, speed) and the transcripts-and-chapters count on the Show page. Two small library fixes: library/show skips criteria/visible, and the app's config overrides every show's routing. |
| Why won't this play? Failures in plain words | S | Pocket Casts has reason strings. Build the diagnostics-to-words table, an explanation in place of the Play button with Try again, and "Last checked, next check" on the Show page. The server-run health check and badge are a podcaster's tool. |
| Private feeds, kept private | M | Baseline, not a feature: AntennaPod already keeps credentials apart from the URL. Basic-auth media can't go into an audio element, so only token-URL hosts play; gate the proxy's Authorization forwarding before the server goes public. |
| New since you were here: the Inbox grouped by day after days away | S | A grouping, not a feature; worth doing when the Inbox is next touched. |
| Snooze a show until a date | M | A :paused? setting podcast-clj's needs list already names; useful for a summer break, rarely. |
| A colophon for About | S | Home's About reads "To be written." A colophon of the typeface, the paper, the colours and the libraries fits the book. |
| Your own keys: remappable shortcuts with a ? sheet | M | play-nice's controls take a keymap; shortcuts are expected on the desktop, not remembered. |
| Said in plain words: a sentence for Explicit, Made with AI, the licence and a locked feed | S | Small and kind; put it in the Details fold. |
| Previews for shared links | L | Needs the server to render metadata for a page, against the app's rule of no rendering on the server. Wait for a public server. |

## Ideas set aside, and why

The judges dropped these. Each has a cheap residue, noted where one exists.

| Idea | Why it was set aside |
| --- | --- |
| On this day: what you heard a year ago | Talk audio is stale a year on, a new listener sees nothing for a year, and stars don't exist yet. Residue: a footer line on a day's page of the diary. |
| Reply as yourself on Nostr | The audience is listeners who hold a Nostr key, and the button couldn't reply to the Bluesky and Mastodon threads where podcasters put their conversations. A key kept in the page is a liability. Revisit with extension or remote signers only, and AT Protocol replies first. |
| The maker's soundbites as pull quotes | About 3% of items carry one, and of 200,000 soundbites Podverse found 50 with titles. Apple already plays the trailer. Residue: a Highlights row once clip play exists, and the trailer as the Show page's taste. |
| References at the back: the notes' links as a reading list | Show-notes links are mostly sponsors, handles and Patreon, and the page titles need the server to fetch arbitrary pages. Residue: a Links list from the notes' own anchor text, with no fetching. |
| On air and coming up: live episodes | Three of 5,000 sampled feeds carry a liveItem, and a reminder works only while a tab is open. Residue: a "Live now" line on a Show page that has one, playable through the existing player. |
| Who gets paid, and the credits as they roll | Value blocks are in a fraction of feeds and in no Danish one, and the Podcasting 2.0 show itself said in July 2026 that streaming payments are over. Residue: one plain line under Support when a feed carries a block. |
| A quote card in the show's colours | Every player that built sharing chose sound, a listener shares a quote a few times a year, and canvas typography is a step below the page. Build the text quote first; if revisited, render from the page's own DOM. |
| Boost from the browser | Real money and a spending secret in IndexedDB, for an audience that is vanishing; Podverse and CurioCaster already boost through Alby. The funding link the app shows covers 93,000 feeds with no risk. |
| Sign in as your website | A login is a cost, not a feature. Gate the server with Tailscale while it serves one listener, and choose passkeys or OIDC, with IndieAuth as an option, when multi-user arrives. |

## What the sceptic said

An Opus 5.5 sceptic read the eighteen sections against the app's principles and checked more than thirty of the code references, which all held to within a line. Nothing is fatal: no finalist needs an account or a server that holds the listener's data. Of its 45 objections, 23 are serious; the sections above were corrected where a sentence was wrong, and the rest stand here.

| Finalist | Objection | Answer |
| --- | --- | --- |
| 1 Read along | The headline isn't rare: Castamatic 13 and Overcast follow the publisher's transcript. The CC key adds to a player whose rows are settled. | Lead with the heard-against-unheard ink, the wake lock and the caption line; put the key in the Controls row. |
| 5 Spoken-word search | The first slice searches about twenty transcripts and forgets them at a reload; the rarity is unchecked against Metacast and Podwise; the effort is near L with heap risk on a phone. | Call the first slice a proof; build the transcript cache before promising the feature. |
| 3 Marks | Clips are keyed by their start's whole second, so two highlights in one cue replace each other. | Key clips by start and end, a small podcast-clj change. |
| 4 Quote and share | The claim that hosts don't count a `#t=` play was wrong; a private feed's file link leaks the subscriber's token; the share dialog is the largest new UI in a compact player, and its app link and QR open nothing while the server is localhost-only. | Corrected above; refuse the file link for private feeds; ship the quotation first and the dialog later. |
| 7 The shelf | Strong colour on a general page you keep neutral; assigned colours stop being the artwork's; vertical titles and sideways scrolling work against the 320 px and accessibility principles; a tap leaves the page before a long title can be read. | Turn a colour only when two seeds collide; reveal the title on the first tap. Or drop it, as the sceptic would. |
| 9 Heard stretches | The section said persistence was required, yet the first slice ships without it. | Corrected above; persistence is the second slice. |
| 11 Calendar | The first slice is what Pocket Casts already shows; the rare parts wait on an unwritten regularity test. | Write the test in `feed.schedule` with the first slice. |
| 12 Person pages | 2.8% of feeds carry the tag, and DR's probably none, so the page may never appear in your own library. | Measure coverage over your library before building past the first slice. |
| 13 Audiobook | The first slice works for one visit; every browser asks for the folder again, and only the tab holding the files can play them. | Say so in the UI; the handle in IndexedDB is the real feature on Chromium. |
| 16 Offline | The first slice leaves out the allow rule, the download runner with resume and the operations: it is an M with a server part. Without a service worker, downloads fill only while the app is open, and iOS evicts storage after seven days unless installed. | Corrected above; make install the first step on a phone. |
| 14 Sync | An iPhone gets only hand sync, three steps per device each time, which risks the silent drift the feature means to cure; YourPods and AntennaPod already sync through a listener's own Nextcloud. | Corrected above; WebDAV through a same-origin route is the phone's path. |
| 15 Share into | An installed app on a phone can't reach a localhost server with no login; until the server is public it is a desktop demo. | The decision on a public server unblocks this and three others. |
| 17 Conversation | 0.4% of items name a thread, so nearly every Episode page shows nothing. | Keep it folded and absent when there is nothing; measure over your library. |
| 18 Floating window | It needs a part play-nice's own docs rule out, only desktop Chromium and Firefox have the API, and Spotify already has a Document PiP miniplayer; the distinct part needs a timed transcript, which 17% of feeds carry. | Drop, or treat as a small library of its own later. |

**Build first**, by the sceptic: who speaks with the read-along mark merged in, as one pure view over the document already read plus the speaker-prefix fix; marks, since `profile.marks` exists, syncs and is unused and the demand is documented; the serial ribbon, one pure function and one view with no new state. **Drop**, by the sceptic: the floating window, the spoken-word search until a transcript cache exists, and the shelf. I keep the search and the shelf in the list with the objections shown, since both are what a listener would remember; the call is yours.

**Two fixes regardless of any feature:** the player's whole-seconds function floors only the position, so the float `:played` and `:buffered` ranges change the store several times a second; and podcast-clj's speaker prefix matches only ASCII letters. **Before building:** measure transcript, person and thread coverage over your own library, and decide on a public server with a login, which unblocks the quote links into the app, share-into on a phone, the offline allow rule and the calendar's webcal at once.

## Where to start

The first slices are small enough to build in the order that finishes one page at a time. This is my order, after the sceptic's pass: it agreed on the first step and would drop three finalists, which the section before this one records.

1. **The Episode page as a book.** The read-along mark (1), the transcript set like a play (2), Quote this (4) and Print (6) are four S's over the transcript the fold already reads. Fix the speaker prefix in podcast-clj first, so "Åse:" is a speaker, and add `transcript/sound-cue?`.
2. **The Library and Show pages as a bookcase.** The Shelf layout's first slice (7) and the serial ribbon (8).
3. **The player.** Mark this moment (3) and the heard stretches (9), which share the seek bar and the keymap; then I have 35 minutes (10) on the Queue page.
4. **Two afternoons.** The manifest and share target (15), and the "Expected …" row (11).
5. **Sync by hand, in words (14)**, which ships in every browser, then the folder and the WebDAV store.
6. **The Ms.** The live caption line and its track; the transcript store and the podcast-clj change behind the library search (5); the person page (12); the audiobook folder (13); the calendar's .ics.
7. **The Ls, once the server has a public address.** Offline episodes (16), and the floating window (18) as a new library part.

Library work the slices need, each small: the Unicode fix and the sound-cue predicate in the transcript namespace; a `:note` on marks; a `:remaining` criterion; a transcript function threaded through the search documents; a public prediction and a regularity test in `feed.schedule`; two range helpers in play-nice; and a browser test of play-nice's tracks on an `<audio>` element.

**Found late, judged after the fact.** A completeness critic compared the inventory with the 48 ideas and named eight more that no lens proposed; a judge graded them in the morning by the same rules. Six are kept, two dropped. Three of the six would rank with the finalists: the map, the privacy readout and teaching the picks.

| Idea | Verdict | Effort | First slice |
| --- | --- | --- | --- |
| A map of your library: every show and episode about a place, pinned on the app's own SVG map in ink and the show's colour, each pin playing; a place page over the location criterion | Keep; unseen, wow 4 | M | A Places section on Library over `resolve/locations`, grouped by place, shown only when the library has any; podcast:location is in 2% of feeds |
| Who learns you listened: which hosts the audio passes through and which images in the notes would phone home, with images loaded through the app's server or not at all | Keep; unseen, wow 3 | M | A Settings switch for images in show notes, and a Details row "Audio from <host>" from the enclosure URL with OP3's prefix unwrapped, no request needed |
| Teach the picks: "Fewer like this" and "More like this" on a pick row, rated into the profile and fed to `recommend`, with the shared-words line saying which words lost weight | Keep; no app shows the model react | S | "Fewer like this" on a pick row through a `:marks/rate` operation and `:unlike` in the picks' options |
| How many are listening: OP3's openly counted downloads on the Show page, with how they are counted | Keep; rare, wow 3 | M | A Details row linking to the show's public OP3 page where the enclosures carry its prefix; the counts need an API key on the server |
| Fresh within the minute: Podping on the thin server, so announced episodes land within a minute | Keep, Podping only | L | A Show page fact "Announces new episodes instantly" from the feed's podcast:podping, and a count on Library |
| New and rising in your language: a "Top in Denmark" shelf for the day the library is empty | Keep, empty library only | S | `fetch-chart!` for the country and language over Apple and fyyd, cached a day |
| Keep it as a file: an MP3 with the show, artwork and your bookmarks written in as ID3 chapters | Drop | L | The bytes are the hard part, which is the offline work; residue: export bookmarks as a JSON Chapters file |
| The show's own website, read through microformats for host cards and full posts | Drop | M | Coverage unknown and likely tiny; measure it on a corpus first |

## Sources

Each feature cites its own sources in its section; these are the ones the research as a whole rests on. The code was read in the checkouts beside the app on 25 October 2026: podcast-clj 0.5.0 and its `doc/background` studies (the corpus study of 5,000 feeds gives the coverage figures), play-nice 0.7.0, like-minded 0.9.1, mind-meld 0.6.0, colour-coded 0.1.0, and the app at branch feature/ui.

**The landscape**

- [Apple Podcasts transcripts](https://podcasters.apple.com/support/5316-transcripts-on-apple-podcasts) and the [March 2024 announcement](https://www.apple.com/newsroom/2024/03/apple-introduces-transcripts-for-apple-podcasts/)
- Pocket Casts: [episode transcripts](https://support.pocketcasts.com/knowledge-base/episode-transcripts/), [highlighted transcripts](https://blog.pocketcasts.com/2026/06/24/highlight-transcripts/), [bookmarks](https://support.pocketcasts.com/knowledge-base/bookmarks), the [bookmarks, notes and sharing](https://forums.pocketcasts.com/forums/topic/bookmarks-notes-and-sharing/) thread, [smart playlists](https://support.pocketcasts.com/article/episode-filters/), [sync](https://support.pocketcasts.com/knowledge-base/pocket-casts-sync), [offline](https://support.pocketcasts.com/knowledge-base/can-i-use-this-service-offline/)
- [Overcast's transcripts](https://9to5mac.com/2026/04/08/overcast-launches-podcast-transcripts-in-new-app-update-for-iphone/), [Castamatic 13](https://castamatic.com/blog/2026/04/20/castamatic-13/) and [Castamatic 11](https://castamatic.com/blog/2025/06/20/castamatic-11/)
- AntennaPod: [automatic downloads](https://antennapod.org/documentation/automation/automatic-downloads), the socialInteract [pull request](https://github.com/AntennaPod/AntennaPod/pull/7127) and [forum request](https://forum.antennapod.org/t/opening-episode-root-posts-objects-to-support-cross-app-comments/2769)
- [Snipd](https://www.snipd.com/) and its [Obsidian export](https://forum.obsidian.md/t/snipd-plugin-to-sync-your-podcast-highlights-to-obsidian-with-transcripts-ai-notes-summary-metadata/26499); [Metacast](https://apps.apple.com/app/metacast/id6462012536); [Audioscrape](https://www.audioscrape.com/docs/features/search); [CueSesh](https://cuesesh.com/); [Attunex](https://hunted.space/product/attunex) and [PodcastIntel](https://apps.apple.com/us/app/-/id6760278262)
- The review analysis putting sync failures at 19% and download failures at 16% of negative reviews: [unstar.app](https://unstar.app/blog/)
- James Cridland, [Comments for podcasting](https://james.cridland.net/blog/2026/comments-for-podcasting/); Podnews, [Who indexes what](https://podnews.net/article/who-indexes-what); Spotify's [interaction figures](https://newsroom.spotify.com/?p=29987)
- Spotify's [Picture-in-Picture miniplayer](https://developer.chrome.com/blog/spotify-picture-in-picture); Open Library's [virtual shelf](http://openlibrary.org/docs/virtualshelf)

**Standards and platform**

- Podcasting 2.0 tags: [transcript](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/transcript.md), [person](https://podcasting2.org/docs/podcast-namespace/tags/person), [socialInteract](https://podcasting2.org/docs/podcast-namespace/tags/social-interact), [updateFrequency](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/update-frequency.md), [medium](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/medium.md), [integrity](https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/integrity.md)
- [WebVTT](https://www.w3.org/TR/webvtt1/); [Media Fragments URI](https://www.w3.org/TR/media-frags/); [Screen Wake Lock](https://www.w3.org/TR/screen-wake-lock/); [CSS Paged Media](https://www.w3.org/TR/css-page-3/); [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545)
- HTML's [played attribute](https://html.spec.whatwg.org/multipage/media.html#dom-media-played); [File System Access](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API) and the [origin private file system](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system); [Web Share Target](https://developer.chrome.com/docs/capabilities/web-apis/web-share-target); [Badging](https://caniuse.com/mdn-api_navigator_setappbadge); [Document Picture-in-Picture](https://wicg.github.io/document-picture-in-picture/); Safari 26's [installability](https://webkit.org/blog/17333/webkit-features-in-safari-26-0/)
- W3C WAI on [transcripts](https://www.w3.org/WAI/media/av/transcripts/); the [BBC subtitle guidelines](https://www.bbc.co.uk/accessibility/forproducts/guides/subtitles/)

**Research**

- Dingman, Tigwell and Shinohara, [Designing a podcast platform for Deaf and hard of hearing users](https://dl.acm.org/doi/10.1145/3441852.3476523), ASSETS '21
- Park et al., [IMX 2024](https://dl.acm.org/doi/10.1145/3639701.3656324), on jumping to segments; the [TREC Podcasts Track](https://trecpodcasts.github.io/)
- Harrison et al., [Frontiers in Psychology 2023](https://pmc.ncbi.nlm.nih.gov/articles/PMC9885971/), on listening and context; Groot Kormelink et al., [Journalism Studies 2025](https://www.tandfonline.com/doi/abs/10.1080/1461670X.2025.2487857)
- Machado, Oliveira and Fernandes, [simulating colour vision deficiency](https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html), IEEE TVCG 2009
- Kleppmann et al., [Local-first software](https://www.inkandswitch.com/local-first/), Ink & Switch 2019

The working files of the research, the inventory of 260 capabilities, the landscape, the 105 ideas, the judges' files and the deep dives, were in the session's scratchpad under `research/`.
