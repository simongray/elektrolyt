(ns dk.simongray.elektrolyt.library.picks.ui-scenes
  "Scenes of the picks on each page that shows them, found as in the app,
  in a library of a few shows whose notes share some words."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.elektrolyt.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(defn art
  "A square of the `color` in place of a show's artwork, which needs no
  network."
  [color]
  (str "data:image/svg+xml,"
       (js/encodeURIComponent
        (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'>"
             "<rect width='1' height='1' fill='" color "'/></svg>"))))

(defn feed
  "A feed titled `title` in English, with artwork of `color`, of the
  `episodes`, pairs of a title and notes, the first the newest."
  [title color episodes]
  {:rss/title    title
   :rss/language "en"
   :itunes/image {:href (art color)}
   :rss/items    (vec (map-indexed (fn [i [t notes]]
                                     {:rss/title       t
                                      :rss/description notes
                                      :rss/guid        {:content (str title "-" i)}
                                      :rss/pub-date    (js/Date. (- 1792000000000 (* i 7 24 3600 1000)))
                                      :rss/enclosure   {:url (str "https://example.org/" i ".mp3") :type "audio/mpeg"}
                                      :itunes/duration (+ 1800 (* 600 i))})
                                   episodes))})

(def feeds
  "The shows of the library, by a short name."
  {:podnews  (assoc (feed "Podnews Weekly Review" "#3a6ea5"
                          [["Boostagrams and value for value" "Listeners send boostagrams with sats over the Lightning network to podcasters."]
                           ["The podcast namespace in apps" "Apps adopt chapters, transcripts and the podcast namespace."]])
                    :podcast/podroll {:remote-items [{:feed-guid "fountain"} {:feed-guid "citadel"}]})
   :citadel  (feed "Citadel Dispatch" "#c0392b"
                   [["Nostr relays and zaps" "Zaps on nostr relays pay creators in sats over Lightning."]
                    ["Self custody" "Holding your own keys, and backups."]])
   :fountain (feed "Fountain Radio" "#d35400"
                   [["Value for value, a year on" "Boostagrams, sats and splits: what value for value pays podcasters."]
                    ["Clips and chapters" "Sharing clips of chapters of an episode."]])
   :defn     (feed "defn" "#27ae60"
                   [["REPL-driven development" "Clojure at the REPL, with data and pure functions."]
                    ["Datomic in production" "Clojure and Datomic at scale, with data as values."]])
   ;; shows of other topics, whose notes use the same everyday words as the
   ;; others, as most shows do, so that the picks aren't about those words
   :design   (feed "99% Invisible" "#8e44ad"
                   [["The hidden life of garbage trucks" "A look at the design of a city, and what it takes for the trucks to work."]])
   :gardens  (feed "Gardeners' Question Time" "#b7950b"
                   [["Roses in spring" "A look at roses, and at what to feed them for the spring, with tips."]])
   :history  (feed "History Hour" "#5d6d7e"
                   [["The fall of the republic" "A look at Rome, and at the road to the republic, with the sources for it."]])
   :music    (feed "Song Exploder" "#16a085"
                   [["A song in parts" "A look at a song, and at the parts of it, with the band, to an hour."]])
   :stars    (feed "The Sky at Night" "#1f3a5f"
                   [["Comets" "A look at comets, and at their tails, with a telescope to the night."]])})

(def shelf
  "A library subscribed to the shows of feeds."
  (reduce (fn [lib [k f]] (library/subscribe lib (str "https://example.org/" (name k) ".xml") f))
          (library/make "scenes")
          feeds))

(def keys-by-title
  "The key that the shelf keeps each show under, by its title."
  (into {} (map (fn [[k {:keys [feed]}]] [(:rss/title feed) k])) (:feeds shelf)))

(defn id
  "The id of the episode `i`, from 0, of the show `k` of feeds."
  [k i]
  (let [title (:rss/title (feeds k))]
    [(keys-by-title title) (str title "-" i)]))

(defn listened
  "The state of a page named `route-name` of the shelf, with the episodes
  `heard` heard, the episodes `queued` queued, and the first of them in
  the player."
  [route-name heard queued]
  (let [lib (as-> shelf lib
              (reduce #(library/update-profile %1 profile/progress %2 600) lib heard)
              (reduce #(library/update-profile %1 profile.queue/enqueue %2) lib queued))]
    (cond-> {:route {:name route-name} :library lib}
      (seq queued) (assoc :player {:id    (first queued)
                                   :media {:paused true :current-time 600 :duration 1800}}))))

(def listener
  "The state of a listener who heard about value for value, and queued a
  few episodes."
  (listened :route/home
            [(id :podnews 0)]
            [(id :defn 0) (id :design 0) (id :gardens 0) (id :history 0) (id :citadel 1)]))

(defscene home
  "The start of the queue, what plays marked, beside the picks like what
  the listener heard last on a wide screen, and About."
  (ui/page listener))

(defscene home-for-a-new-listener
  "About first, for a listener with no shows yet."
  (ui/page {:route {:name :route/home} :library (library/make "scenes")}))

(defscene after-the-queue
  "The picks at the end of the queue, like what's in it, of its shows too."
  (ui/page (assoc listener :route {:name :route/queue})))

(defscene under-an-episode
  "The picks like an episode, after its Details, once the listener opened
  them."
  (let [[k item-guid :as episode-id] (id :podnews 0)]
    (ui/page (assoc listener
                    :route {:name        :route/episode
                            :path-params {:guid k :hash (guid/short-hash item-guid)}}
                    :open  {[:picks :episode episode-id] true}))))

(defscene under-a-show
  "The shows that a show recommends in its podroll, and the episodes of
  other shows like it, once the listener opened them."
  (let [k (keys-by-title "Podnews Weekly Review")]
    (ui/page (assoc listener
                    :route  {:name :route/show :path-params {:guid k}}
                    :open   {[:picks :show k] true}
                    :linked {k {:status  :done
                                :podroll [{:guid "fountain" :title "Fountain Radio" :artwork (art "#d35400")}
                                          {:guid "citadel" :title "Citadel Dispatch" :artwork (art "#c0392b")}
                                          {:guid "boostagram-ball" :title "Boostagram Ball"}]}}))))
