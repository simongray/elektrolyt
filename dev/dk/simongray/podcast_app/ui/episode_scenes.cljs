(ns dk.simongray.podcast-app.ui.episode-scenes
  "Scenes of the Episode page: an episode from the start of its show's
  feed, with its chapters once they're read, with the Details of one that
  says much of itself, in another language, with a transcript in another
  language than its feed, while it plays, and the ways it can be missing."
  (:require [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.scenes :refer [artwork url]]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.ui.episode :as episode-ui]
            [portfolio.replicant :refer [defscene]]))

(def show-guid
  "The guid of the scenes' show, which its URL gives."
  (guid/from-url url))

(def item
  "The scenes' episode, with show notes whose times play it and a
  transcript."
  {:rss/title       "Episode 275: \"Off The Rails\""
   :rss/guid        {:content "https://example.org/?p=275"}
   :rss/pub-date    (js/Date. 1792000000000)
   :itunes/duration 5994
   :itunes/image    {:href artwork}
   :rss/enclosure   {:url "https://example.org/275.mp3" :type "audio/mpeg"}
   :rss/description (str "<h1>Show notes</h1>"
                         "<p>What is the future of independent podcast apps? "
                         "At 12:34 Adam and Dave disagree, and at 1:02:03 they "
                         "make up.</p><h2>Links</h2>"
                         "<ul><li><a href=\"https://podcastindex.org\">Podcast Index</a></li></ul>")
   :podcast/transcripts [{:url "https://example.org/275.vtt" :type "text/vtt"}]})

(def feed
  "The start of the feed of the scenes' show, with its episode."
  {:rss/title "Podcasting 2.0" :rss/items [item]})

(def hash-275
  "The short hash of the guid of the scenes' episode, which names its page."
  (guid/short-hash "https://example.org/?p=275"))

(defn episode-page
  "The state of the Episode page with what :feeds keeps of the feed of its
  show, `known`, if anything, while the whole feed is fetched, and the
  `more` state merged in."
  ([known]
   (episode-page known {}))
  ([known more]
   (merge {:route    {:name         :route/episode
                      :path-params  {:guid show-guid :hash hash-275}
                      :query-params {:url url}}
           :feeds    (if known {url known} {})
           :requests {[:whole url] {}}}
          more)))

(def from-the-start
  "What :feeds keeps of the feed of the scenes' show once its start is in."
  {:feed feed :whole? false})

(defn id
  "The id of the episode of the scenes."
  []
  (:id (episode-ui/route-page (episode-page from-the-start))))

(def read-chapters
  "What the files of the scenes' episode say once they're read: a chapters
  file, one of whose chapters links to a web page."
  {:status :read
   :result {:chapters {:source   :file
                       :document {:chapters [{:start-time 0 :title "Intro"}
                                             {:start-time 418 :title "\"Normal People Won't Do This\" — The Robot's Podcast Suggestions"}
                                             {:start-time 3723 :title "Value for value"
                                              :url "https://podcasting2.org/docs/podcast-namespace/tags/value"}]}}}})

(defscene from-the-start-of-the-feed
  "The episode as soon as the start of its show's feed has it, with its show
  notes, whose times play it from there, and whose headings sit below the
  page's own."
  (ui/page (episode-page from-the-start)))

(defscene with-its-chapters
  "The chapters once the chapters file is read and the listener opened
  them, whose titles link to the web page of a chapter that names one."
  (ui/page (episode-page from-the-start {:episodes {(id) read-chapters}
                                         :open     {[:chapters (id)] true}})))

(defscene from-a-time
  "A link to the episode at a time, e.g. ?t=1m13s."
  (ui/page (assoc-in (episode-page from-the-start) [:route :query-params :t] "1m13s")))

(defscene while-it-plays
  "The episode in the player, with the browser's own controls for now."
  (let [page (episode-ui/route-page (episode-page from-the-start))]
    (ui/page (episode-page from-the-start
                           {:player (assoc (episode/playable page) :playing? true)}))))

(def with-details
  "The episode with what it says of itself: its number, its people with a
  guest among them, the place that it's about, where else to watch it, and
  the size of its file."
  (assoc item
         :podcast/episode       {:content 275}
         :itunes/explicit       true
         :podcast/people        [{:content "Adam Curry" :role "host"
                                  :img "https://feeds.podcastindex.org/adam_avatar.jpg"}
                                 {:content "Dave Jones" :role "host"
                                  :img "https://feeds.podcastindex.org/dave_avatar.jpg"}
                                 {:content "Sam Sethi" :role "guest"}]
         :podcast/locations     [{:content "Birmingham Civil Rights Museum" :rel "subject"
                                  :geo {:lat 33.5159981 :lon -86.8146098}}]
         :podcast/content-links [{:href "https://www.youtube.com/watch?v=pc20" :content "Watch on YouTube"}]
         :podcast/funding       [{:url "https://paypal.me/podcastindex" :content "Support us with Value 4 Value"}]
         :rss/enclosure         {:url "https://example.org/275.mp3" :type "audio/mpeg" :length 95904000}))

(defscene with-its-details
  "An episode that says much of itself: its number and Explicit with its
  date, and its Details beside the show notes, or below them on a phone,
  with the links that support the show."
  (ui/page (episode-page {:feed (assoc feed :rss/items [with-details]) :whole? false})))

(def german
  "The start of a feed in German, with the scenes' episode in German."
  {:rss/title    "Wortschatz"
   :rss/language "de"
   :rss/items    [(assoc item
                         :rss/title       "Folge 275: Die Rechtschreibreform"
                         :rss/description (str "<p>In dieser Folge geht es um die Rechtschreibreform, "
                                               "um die Silbentrennung und darum, warum zusammengesetzte "
                                               "Wörter wie Donaudampfschifffahrtsgesellschaftskapitän im "
                                               "Deutschen so lang werden können. Ab 12:34 streiten wir "
                                               "über das Eszett.</p>"))]})

(defscene in-another-language
  "An episode whose show's feed is in German: its title, its show's title
  and its notes have the feed's language, so that the browser hyphenates
  them by German rules and a screen reader reads them in German, while
  the app's own words stay English."
  (ui/page (episode-page {:feed german :whole? false})))

(def spanish-transcript
  "The reference to a transcript in Spanish, which the podcast namespace's
  transcript tag names."
  {:url "https://example.org/275.es.vtt" :type "text/vtt" :language "es"})

(def read-spanish-transcript
  "What the files of the scenes' episode say once its transcript in Spanish
  is read."
  {:transcript-status :read
   :transcript        (assoc spanish-transcript
                             :document {:segments [{:start-time 0 :end-time 9 :speaker "Adam Curry"
                                                    :body "Bienvenidos a Podcasting 2.0. Hoy hablamos del futuro de las aplicaciones independientes de pódcast."}
                                                   {:start-time 9 :end-time 21 :speaker "Dave Jones"
                                                    :body "Y de la interoperabilidad, que es extraordinariamente importante para la descentralización."}
                                                   {:start-time 754 :end-time 760 :speaker "Adam Curry"
                                                    :body "Aquí, desafortunadamente, no estamos de acuerdo."}]})})

(defscene with-a-transcript-in-another-language
  "An episode of a show in English whose transcript is in Spanish: once
  it's shown, the transcript has the language that its tag names, so that
  a screen reader reads it in Spanish, while the show notes have the
  feed's."
  (ui/page (episode-page {:feed   (assoc feed
                                         :rss/language "en"
                                         :rss/items [(assoc item :podcast/transcripts [spanish-transcript])])
                          :whole? false}
                         {:episodes {(id) read-spanish-transcript}
                          :open     {[:transcript (id)] true}})))

(defscene while-its-feed-loads
  "A link to an episode beyond the start of the feed, while the whole feed
  is fetched."
  (ui/page (episode-page nil)))

(defscene an-episode-the-feed-has-dropped
  "A link to an episode that the whole feed doesn't have any more."
  (ui/page (assoc-in (episode-page {:feed feed :whole? true} {:requests {}})
                     [:route :path-params :hash] "0000000")))
