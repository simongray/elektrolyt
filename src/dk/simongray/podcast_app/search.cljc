(ns dk.simongray.podcast-app.search
  "What the Search page finds for a query: the shows that each directory
  has for words, or the feeds at a link that the listener pasted.

  The state keeps a search under its query. One of words is a map of the
  :sources that it asks, in the listener's order, and their :lists by
  source, each a map of its :status, :searching, :done or :failed, its
  :results and, where it failed, the :reason, and the source whose list
  the listener last jumped to as :shown. One of a link is a map of
  its :status and its :results. A result is the map of a show's feed
  :url, its :guid, :title, :author, :artwork and :artwork-sizes. The guid
  is the one that the feed declares, as far as the directory knows it, or
  else the one that its URL gives.

  Each directory's list is its own answer, in its own order, which no
  other answer changes, so a list stays as it appeared."
  (:require [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.profile :as profile]))

(defn link?
  "Whether the query `q` reads as a link, i.e. the URL of a feed, a domain,
  a web page or a link of an app, rather than as words for the directories."
  [q]
  (not (:error (discovery/read-input q))))

(defn show->result
  "The result of a `show` of a directory, or nil when it names no feed."
  [show]
  (when-let [url (resolve/self-url show)]
    {:url           url
     :guid          (guid/feed-guid show url)
     :title         (resolve/title show)
     :author        (resolve/author show)
     :artwork       (resolve/artwork show)
     :artwork-sizes (resolve/artwork-sizes show)}))

(defn feed->result
  "The result of a `feed` that discovery found at a link."
  [{:keys [url title feed] :as found}]
  {:url           url
   :guid          (guid/feed-guid feed url)
   :title         (or (resolve/title feed) title)
   :author        (resolve/author feed)
   :artwork       (resolve/artwork feed)
   :artwork-sizes (resolve/artwork-sizes feed)})

(defn sources
  "The directories that a search asks for the listener of the library
  `lib`, in their order: the :search-sources of their preferences, or of
  the defaults before the library is restored, and the Podcast Index only
  where the app's server signs the requests to it, `index?`."
  [lib index?]
  (let [prefs (if lib
                (profile/preferences (:profile lib))
                profile/default-preferences)]
    (filterv #(or index? (not= :podcastindex %)) (:search-sources prefs))))

(defn searching
  "A search of the directories `sources`, none of which has answered."
  [sources]
  {:sources (vec sources)
   :lists   (zipmap sources (repeat {:status :searching}))})

(defn shows->list
  "The list of one directory, from its `answer`, the map of
  directory/search! for that source alone: :done with its :results, or
  :failed with the :reason."
  [{:keys [results report] :as answer}]
  (if-let [{:keys [reason]} (first report)]
    {:status :failed :results [] :reason reason}
    {:status :done :results (into [] (keep show->result) results)}))

(defn failed
  "The list of a directory whose search threw `reason`, a message."
  [reason]
  {:status :failed :results [] :reason reason})

(defn results-of
  "Every result of the `search`, of a link or of the directories, the
  latter in the order of their lists."
  [{:keys [results sources lists] :as search}]
  (or results
      (into [] (mapcat #(get-in lists [% :results])) sources)))

(defn searching?
  "Whether the `search` waits for an answer."
  [{:keys [status lists] :as search}]
  (boolean (or (= :searching status)
               (some #(= :searching (:status %)) (vals lists)))))

(defn feeds->search
  "The search of the feeds that discovery/find-feeds! found at a link, the
  map `found`. Only the podcasts count, not e.g. the feed of a blog."
  [{:keys [feeds] :as found}]
  {:status  :done
   :results (into [] (comp (filter :podcast?) (map feed->result)) feeds)
   :failed  []})

(defn source-name
  "The name of the directory `source-id` for the listener."
  [source-id]
  (get-in directory/sources [source-id :name] (name source-id)))

(defn subscribed-urls
  "The set of the feed URLs that the library `lib` is subscribed to, as
  podcast-clj normalizes them, or nil before the library is restored."
  [lib]
  (when lib
    (into #{}
          (keep #(some-> % val :url guid/normal-url))
          (profile/subscriptions (:profile lib)))))

(defn subscribed?
  "Whether the feed at `url` is among the `subscribed` URLs, a set of
  subscribed-urls."
  [subscribed url]
  (contains? subscribed (guid/normal-url url)))

(comment
  (link? "podcasting 2.0")
  ;; => false
  (link? "atp.fm")
  ;; => true
  #_.)
