# Podcasting 2.0 tags in a listener's library, October 2026

*Measured on 25 October 2026 for the features in [Unique features for
Elektrolyt](unique-features-2026.md), whose sceptic asked for the coverage
of transcripts, people and threads over a listener's library before
building past the first slices.*

The corpus study in podcast-clj measured a random sample of the Podcast
Index, but a listener follows the shows that are popular where they live.
So this report measures a stand-in library instead: 368 shows from the
charts of six countries, six categories and three directories, and DR's
own feeds. I fetched each feed once and counted each tag over the show and
its 20 newest episodes. The shows and what each carries are in
[coverage-2026-shows.csv](coverage-2026-shows.csv).


## Summary

- **Transcripts are the only common tag,** on 14% of shows and 11% of new
  episodes, and nearly all are timed WebVTT. The host decides: 21 of the
  25 shows at Podigee have them, and none of the 126 at Megaphone,
  Simplecast and Acast.
- **A Danish listener meets almost none of the tags.** DR's 43 feeds use
  only podcast:locked, and 3 of the other 62 Danish shows have
  transcripts. In the German charts, 39% of shows have them.
- **Transcripts rarely name their speakers.** Of the 52 transcripts, 2
  name them, 35 label them "Speaker 0" or "SPEAKER_01", and 15 name no
  one.
- **Chapters come from the show notes.** Timestamps in the notes give
  chapters to 10% of new episodes, a chapters file to 1.6%.
- **The rest are rare.** People are on 4% of shows, 9 of the 15 at one
  French host; funding, locations and podping are under 4%; comment
  threads are on 3 shows, value blocks on 2, a soundbite and a stated
  schedule on 1 each.
- **Serial shows are three times as common as in the corpus,** 4.6%
  against 1.5%, since the charts favour narrative series.
- **The features report's 29% of popular feeds with a transcript is
  wrong.** That figure is from the corpus study's first 605 feeds, which
  leaned to Podcasting 2.0 hosts. The study's popular stratum has 17%, as
  all its feeds do.


## The library

The charts were fetched on 25 October 2026 with `directory/fetch-chart!`,
one source and one chart per call, a second apart:

| Source | Asked | With a feed |
|---|---|---|
| Apple's top podcasts in Denmark, the US, the UK, Germany, Sweden and France | the top 30 of each | 171 |
| Apple's charts of News, Technology, True Crime, History, Comedy and Education in the US, Germany and Denmark | the top 10 of each | 179 |
| fyyd's chart in German | the top 25 | 25 |
| The Podcast Index's trending in English | the top 25 | 25 |
| DR's own feeds at api.dr.dk with an episode in the year to 10 October, from the index's dump of that day | the 40 with the highest popularity, then the most episodes | 40 |

Apple named no feed for nine of its shows. The remaining 440 listings hold
368 shows, since 66 were listed more than once, e.g. Genstart in Apple's
Danish top 30, its News chart and DR's feeds. Two feed URLs counted as one
when they differed only in the scheme, www, a trailing slash or the case
of the domain, and DR's feeds counted by their slug, since Apple lists
them with queries such as ?format=podcast&limit=100. No two feeds ended at
the same URL after redirects.

By the language of the feed, 130 shows are English, 99 Danish, 87 German,
26 Swedish and 25 French, and one names none.

I fetched each feed once with `podcast/fetch!` and podcast-clj's own
User-Agent, without its later pages: one request at a time per domain, a
quarter of a second apart, and six domains at once. All 368 answered 200
and parsed, and no server answered 429. For the 52 shows with a
transcript, I also fetched the newest episode's transcript to read its
speakers.

The library stands in for many listeners rather than one: a real one
follows a few dozen shows, and charts list what is popular, not what one
person keeps.


## The tags

The 20 newest episodes of each show are 7,027 in all. A show counts when
its channel or one of those episodes carries the tag. An episode counts
when it carries the tag itself or, for people, threads, locations, value
blocks and funding, inherits the show's, as `feed.resolve` reads them. The
corpus columns come from the study's saved measures of 4,648 parsed feeds
and of the 1,977 in its popular stratum, where a feed counts when its
channel or any item carries the tag.

| Tag | Shows | Share | Episodes | Share | Corpus | Popular |
|---|---|---|---|---|---|---|
| podcast:transcript | 52 | 14% | 797 | 11% | 17% | 17% |
| The same, timed | 52 | 14% | 793 | 11% | | |
| podcast:person, on the show | 14 | 3.8% | | | 2.8% | 2.9% |
| podcast:person, on an episode | 12 | 3.3% | 205 | 2.9% | 1.4% | 1.2% |
| podcast:person, on either | 15 | 4.1% | 279 | 4.0% | | |
| podcast:socialInteract | 3 | 0.8% | 56 | 0.8% | 0.4% | 0.4% |
| podcast:location, on the show | 2 | 0.5% | | | 1.9% | 1.3% |
| podcast:location, on an episode | 4 | 1.1% | 29 | 0.4% | 1.5% | 0.3% |
| podcast:location, on either | 5 | 1.4% | 68 | 1.0% | | |
| podcast:chapters | 11 | 3.0% | 111 | 1.6% | 4.8% | 5.4% |
| Podlove chapters | 6 | 1.6% | 65 | 0.9% | | |
| Timestamps in the show notes | 50 | 14% | 731 | 10% | | |
| Chapters of any of the three | 59 | 16% | 827 | 12% | | |
| podcast:updateFrequency | 1 | 0.3% | | | 0.6% | 1.0% |
| podcast:podping | 5 | 1.4% | | | 5.1% | 6.7% |
| podcast:value | 2 | 0.5% | 40 | 0.6% | 0.3% | 0.2% |
| podcast:soundbite | 1 | 0.3% | 10 | 0.1% | 2.9% | 3.1% |
| itunes:type serial | 17 | 4.6% | | | 1.5% | 1.3% |
| podcast:medium | 9 | 2.4% | | | 7.8% | 6.6% |
| podcast:funding | 14 | 3.8% | 219 | 3.1% | 4.6% | 5.8% |

The corpus's serial shares are from the index's own itunesType for the
study's sample, since the study counted only whether a feed has the tag.
Of the nine feeds with podcast:medium, one says video and the rest
podcast. Every episode with Podlove chapters also has a chapters file.

Every show with a transcript has a timed one on some of its new episodes.
Of the 52 transcripts fetched, one per show, 50 are WebVTT, one SRT and
one plain text. Many episodes offer several formats: WebVTT on 754, plain
text on 328, JSON on 311, SRT on 205 and HTML on 159. Over all items
rather than the newest 20, 60 shows have a transcript and 18 a chapters
file, so a few shows have stopped publishing them.

The library has fewer chapters files, soundbites, podpings and media than
the corpus, because 126 of its shows are at Megaphone, Simplecast and
Acast, which write none of the podcast namespace's tags in the table.

A few more tags that the features report's longer list rests on:

| Tag | Shows | Share | Corpus | Popular |
|---|---|---|---|---|
| podcast:guid | 64 | 17% | 25% | 22% |
| podcast:trailer | 12 | 3.3% | 0.7% | 0.6% |
| podcast:alternateEnclosure | 6 | 1.6% | 0.6% | 0.9% |
| podcast:podroll | 2 | 0.5% | 1.1% | 1.0% |
| podcast:liveItem | 0 | | 0.1% | 0.1% |
| itunes:duration | 365 | 99% | | |
| An episode number | 168 | 46% | | |

Eleven of the twelve trailers are at Podigee. Downloads pass through
measurement prefixes too: the newest episode of 81 shows, 22%, goes
through at least one, and 41 of those through two to seven. Podtrac is on
46 shows, Podscribe on 36, Podsights 27, Magellan 17, Claritas 14, Artsai
7, Spotify's own prefix 7, and Chartable and OP3 on one each.


## By country and source

Apple's charts count the top 30 and the categories' top 10 together. DR's
own feeds count as DR wherever they were listed, so Apple Denmark is the
rest of the Danish charts. A show listed in two places counts in both.

| | Shows | Transcript | Person | Chapters file | Notes' timestamps | Thread | Location | Podping | Funding | Serial | Prefixed download |
|---|---|---|---|---|---|---|---|---|---|---|---|
| DR | 43 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 4 | 0 |
| Apple Denmark | 62 | 3 (5%) | 0 | 0 | 4 | 0 | 0 | 1 | 2 | 0 | 6 |
| Apple Sweden | 26 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 2 | 0 |
| Apple Germany | 66 | 26 (39%) | 2 | 4 | 16 | 0 | 0 | 0 | 3 | 2 | 6 |
| Apple UK | 30 | 1 (3%) | 0 | 0 | 7 | 0 | 0 | 0 | 0 | 1 | 15 |
| Apple US | 74 | 9 (12%) | 1 | 1 | 20 | 0 | 1 | 1 | 6 | 7 | 50 |
| Apple France | 25 | 0 | 9 (36%) | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| fyyd, German | 25 | 9 (36%) | 2 | 6 | 3 | 2 | 2 | 0 | 3 | 1 | 6 |
| Podcast Index, English | 25 | 5 (20%) | 1 | 0 | 0 | 1 | 2 | 3 | 2 | 0 | 5 |

- **DR** declares the podcast namespace in every feed and uses only
  podcast:locked from it. None of its 860 new episodes has a transcript,
  a person or chapters, and four of its 43 feeds are serial. With the
  other Danish shows, a Danish listener has transcripts on 27 of 2,017 new
  episodes, 1.3%, all at Spreaker, whose machine transcripts number the
  speakers.
- **Germany** has the most: 29% of its new episodes have a transcript.
  Of its 26 shows with one, 18 are at Podigee, which writes WebVTT with
  numbered speakers, and three are ZDF's. Deutschlandfunk, BR and WDR
  write none.
- **The US** has transcripts on 9 of 74 shows, and chapters as
  timestamps in the notes on 20, eight of them at Megaphone. Two thirds
  of its shows send their downloads through a measurement prefix, against
  none of DR's, Sweden's or France's.
- **France** names people on 9 of 25 shows, all at Audiomeans, and has
  little else.
- **fyyd's and the index's charts** hold most of the rest: all three
  threads, four of the five locations, three of the five podpings and both
  value blocks. One show in the index's trending, Honest Health on a
  PeerTube instance, carries twelve of the tags counted here.


## What a listener meets

The tables say how often a listener with this library would meet each
feature of the report, by the share of shows and new episodes that carry
what the feature needs. For a listener with 30 of these shows, 14% is
about four shows.

| # | Feature | How often |
|---|---|---|
| 1 | Read along | A timed transcript on 14% of shows and 11% of new episodes; for a Danish listener, 1.3% of episodes and none of DR's |
| 2 | Who speaks | Real names in 2 of the 52 transcripts, under 1% of shows; machine labels such as "Speaker 0" in 35 |
| 3 | Highlights and the commonplace book | Marks on every episode; the highlighter on the 11% with a transcript |
| 4 | Quote a passage | The quotation on the 11% with a transcript; the moment's link on every episode |
| 5 | Search the spoken words | 14% of shows, about four of 30; next to none for a Danish listener |
| 6 | Print the episode | Every episode; a table of contents on the 12% with chapters, a transcript on 11% |
| 7 | The shelf | Every show; it needs no tag |
| 8 | Your place in the story | Serial by the feed on 4.6% of shows, four of them DR's; "Hear in order" for the rest |
| 9 | The stretches you heard | Every episode; it needs no tag |
| 10 | I have 35 minutes | A duration on 99% of episodes; a chapter to offer on 12%, mostly from the notes |
| 11 | A calendar of expected episodes | A stated schedule on 1 show of 368; 348 shows, 95%, have ten dated episodes and aren't complete, so the date is a guess nearly everywhere |
| 12 | A page for every person | People on 4% of shows and episodes, about one of 30, and on no Danish show |
| 13 | An audiobook as a show | Any folder; no show in the library calls itself an audiobook |
| 14 | Sync | Every library; it needs no tag |
| 15 | Share into Elektrolyt | Every show a link can name; it needs no tag |
| 16 | Keep three for the train | Every episode; all 7,027 have an enclosure |
| 17 | The episode's conversation | A thread on 0.8% of shows and episodes, none in Apple's charts, all over ActivityPub |
| 18 | A floating reading window | Its paragraph on the 11% with a timed transcript; the rest on every episode |

The late ideas:

| Idea | How often |
|---|---|
| A map of your library | A location on 1.4% of shows and 1.0% of episodes, one of them in Apple's charts; most libraries of 30 would have no pin |
| Who learns you listened | Every episode; 22% of shows send the download through a measurement prefix, two thirds of the US charts' shows and none of DR's |
| Teach the picks | Every library; it needs no tag |
| How many are listening | OP3's prefix on 1 show of 368 |
| Fresh within the minute | Podping on 1.4% of shows, three of the five in the index's trending |
| New and rising in your language | Every empty library; Apple's Danish chart gave a feed for all 30 of its shows |
| Keep it as a file | Every episode; it needs no tag |
| The show's own website | Not measured, since it needs each show's website fetched |


## What it changes

- **Build first what needs no tag.** The shelf, the heard stretches,
  marks, I have 35 minutes, sync and offline episodes reach every show,
  and durations are on 99% of episodes.
- **The transcript features pay off for German shows, less for English
  ones, and hardly for a Danish listener** until DR publishes transcripts.
  Across the library, read along, quotes and the printed transcript reach
  one new episode in nine.
- **The cast line would have names on 2 shows of 368.** Most transcripts
  number their speakers, so the line would read "Speaker 0 · Speaker 1"
  on most shows unless it appears only when the transcript gives names.
- **Most chapters are the notes' timestamps,** on six times as many
  episodes as chapters files, and the app already reads them.
- **The calendar's regularity test is the feature,** since one show states
  a schedule.
- **Keep the rare sections absent when empty,** as the sceptic said. A
  library of 30 shows has a person page from the tag on about one show, a
  thread in one library of five, and a map pin in one of three, so the
  "Named in the show notes" list is the person page most listeners get.
- **The speaker prefix in podcast-clj reads text before a colon as a
  speaker.** In two of the 52 transcripts it found false speakers, e.g. 22
  in a WebVTT file of Mission Energiewende, among them "Also im Klartext"
  and "so".


## How to repeat it

The measuring namespace is [dev/coverage.clj](../dev/coverage.clj). It
reads the index's dump with sqlite3 and the corpus study's measures from
the podcast-clj checkout beside the app, and the Podcast Index's
credentials from `../secrets.edn` or the file that PODCAST_APP_SECRETS
names. From a REPL with dev/ on the classpath, in the app's checkout:

    (require 'coverage)
    (def lib (coverage/library!))
    (coverage/fetch-all! lib)
    (def ms (coverage/measures))
    (coverage/tally ms)
    (coverage/by-group ms)
    (coverage/prefixes ms)
    (coverage/corpus)
    (coverage/transcripts! ms)
    (coverage/csv! ms "doc/coverage-2026-shows.csv")

The charts, the library, the manifest and the feeds go to
target/coverage. The charts change daily, so a second run measures a
different library.
