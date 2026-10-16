(ns dk.simongray.podcast-app.search
  "What the Search page finds for a query: the shows that the directories
  have for words, or the feeds at a link that the listener pasted.

  Either way, the state keeps a search under its query as a map of its
  :status, :searching or :done, its :results and the ids of the
  directories that :failed. A result is the map of a show's feed :url, its
  :title, :author and :artwork."
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
    {:url     url
     :title   (resolve/title show)
     :author  (resolve/author show)
     :artwork (resolve/artwork show)}))

(defn feed->result
  "The result of a `feed` that discovery found at a link."
  [{:keys [url title feed] :as found}]
  {:url     url
   :title   (or (resolve/title feed) title)
   :author  (resolve/author feed)
   :artwork (resolve/artwork feed)})

(defn shows->search
  "The search of what the directories answered so far, the map `answer`
  of directory/search!."
  [{:keys [results report pending] :as answer}]
  {:status  (if (seq pending) :searching :done)
   :results (into [] (keep show->result) results)
   :failed  (mapv :source report)})

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
