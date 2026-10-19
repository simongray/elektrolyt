(ns dk.simongray.podcast-app.ui.episode-scenes
  "Scenes of the Episode page: an episode from the start of its show's
  feed, with its chapters once they're read, with the Details of one that
  says much of itself, while it plays, and the ways it can be missing."
  (:require [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.ui.episode :as episode-ui]
            [dk.simongray.podcast-app.ui.search-scenes :as search-scenes]
            [portfolio.replicant :refer [defscene]]))

(def url "https://feeds.podcastindex.org/pc20.xml")

(def show-guid (guid/from-url url))

(def item
  {:rss/title       "Episode 275: \"Off The Rails\""
   :rss/guid        {:content "https://example.org/?p=275"}
   :rss/pub-date    (js/Date. 1792000000000)
   :itunes/duration 5994
   :itunes/image    {:href search-scenes/artwork}
   :rss/enclosure   {:url "https://example.org/275.mp3" :type "audio/mpeg"}
   :rss/description (str "<h1>Show notes</h1>"
                         "<p>What is the future of independent podcast apps? "
                         "At 12:34 Adam and Dave disagree, and at 1:02:03 they "
                         "make up.</p><h2>Links</h2>"
                         "<ul><li><a href=\"https://podcastindex.org\">Podcast Index</a></li></ul>")
   :podcast/transcripts [{:url "https://example.org/275.vtt" :type "text/vtt"}]})

(def feed
  {:rss/title "Podcasting 2.0" :rss/items [item]})

(def hash-275 (guid/short-hash "https://example.org/?p=275"))

(defn episode-page
  "The state of the Episode page with the `shown` entry of its show in
  :shows, and the `more` state merged in."
  ([shown]
   (episode-page shown {}))
  ([shown more]
   (merge {:route {:name         :route/episode
                   :path-params  {:guid show-guid :hash hash-275}
                   :query-params {:url url}}
           :shows {show-guid shown}}
          more)))

(def from-the-start
  {:url url :status :loading :head feed})

(defn id
  "The id of the episode of the scenes."
  []
  (:id (episode-ui/route-page (episode-page from-the-start))))

(def read-chapters
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
  "The chapters once the chapters file is read, whose titles link to the web
  page of a chapter that names one."
  (ui/page (episode-page from-the-start {:episodes {(id) read-chapters}})))

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
  (ui/page (episode-page {:url url :status :loading :head (assoc feed :rss/items [with-details])})))

(defscene while-its-feed-loads
  "A link to an episode beyond the start of the feed, while the whole feed
  is fetched."
  (ui/page (episode-page {:url url :status :loading})))

(defscene an-episode-the-feed-has-dropped
  "A link to an episode that the whole feed doesn't have any more."
  (ui/page (assoc-in (episode-page {:url url :status :done :feed feed})
                     [:route :path-params :hash] "0000000")))
