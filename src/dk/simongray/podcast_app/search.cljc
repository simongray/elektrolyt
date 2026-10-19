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
  other answer changes, so a list stays as it appeared.

  The cache keeps each list that a directory gave. One that's less than a
  day old answers the search without asking the directory, and an older
  one stands in for a directory that fails, with the :kept-at that it was
  kept."
  (:require [clojure.string :as str]
            [dk.simongray.like-minded.cache :as cache]
            [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.settings :as settings]))

(defn link?
  "Whether the query `q` reads as a link, i.e. the URL of a feed, a domain,
  a web page or a link of an app, rather than as words for the directories."
  [q]
  (not (:error (discovery/read-input q))))

(defn result
  "The result of `x`, a show of a directory or a feed, whose feed is at
  `url`."
  [x url]
  {:url           url
   :guid          (guid/feed-guid x url)
   :title         (resolve/title x)
   :author        (resolve/author x)
   :artwork       (resolve/artwork x)
   :artwork-sizes (resolve/artwork-sizes x)})

(defn show->result
  "The result of a `show` of a directory, or nil when it names no feed."
  [show]
  (some->> (resolve/self-url show) (result show)))

(defn feed->result
  "The result of a `feed` that discovery found at a link, with the title
  of the link's page where the feed has none."
  [{:keys [url title feed] :as found}]
  (update (result feed url) :title #(or % title)))

(defn index-credentials
  "The credentials of the Podcast Index that the page gives podcast-clj in
  the `state`: none of its own, since the app's server signs the requests,
  or nil when the server can't."
  [state]
  (when (get-in state [:server :podcastindex?])
    {}))

(defn sources
  "The directories that a search asks in the `state`, in the listener's
  order from settings/searched, with the Podcast Index only where the
  app's server signs the requests to it."
  [state]
  (let [index? (get-in state [:server :podcastindex?])]
    (filterv #(or index? (not= :podcastindex %)) (settings/searched state))))

(defn searching
  "A search of the directories `sources`, none of which has answered."
  [sources]
  {:sources (vec sources)
   :lists   (zipmap sources (repeat {:status :searching}))})

(defn failed
  "The list of a directory whose search failed for `reason`, a message."
  [reason]
  {:status :failed :results [] :reason reason})

(defn shows->list
  "The list of one directory, from its `answer`, the map of
  directory/search! for that source alone: :done with its :results, or
  failed with the :reason."
  [{:keys [results report] :as answer}]
  (if-let [{:keys [reason]} (first report)]
    (failed reason)
    {:status :done :results (into [] (keep show->result) results)}))

(defn cache-key
  "The key in the cache of the list of the directory `source` for the
  words `q`, which ignores their case and spacing, as the directories do."
  [source q]
  [source (str/lower-case (str/join " " (str/split (str/trim q) #"\s+")))])

;; Apple's Search API has its answers cached for a day, by their max-age
;; https://performance-partners.apple.com/search-api
(defn fresh?
  "Whether the list that the cache `kept` answers a search at the instant
  `at` without asking the directory: when it's less than a day old."
  [kept at]
  (boolean (and kept (< (cache/age kept at) 86400))))

;; RFC 5861, section 4: stale-if-error
(defn stale-list
  "The list of a directory that didn't answer, `l`, with the results of
  the list that the cache `kept` of it and the :kept-at of those, when it
  kept any."
  [l kept]
  (if (seq (get-in kept [:value :results]))
    (assoc l :results (get-in kept [:value :results]) :kept-at (:at kept))
    l))

(defn failed?
  "Whether the `search` failed, as a whole or in one of its directories."
  [{:keys [error lists] :as search}]
  (boolean (or error (some #(= :failed (:status %)) (vals lists)))))

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

(defn submitted
  "The actions of the search for the words or the link `q` that the listener
  submitted in the `state`: put the query in the URL, which searches when
  the state has no search for it, and search again at once when the one in
  the state failed somewhere.

  A search that went well stays, since the cache would give the same
  lists. One that's run again comes first, so that the navigation finds it
  under way and doesn't start another."
  [state q]
  (let [q    (str/trim (str q))
        push [:route/push :route/search {} {:q q}]]
    (cond
      (str/blank? q)                         [[:route/push :route/search]]
      (failed? (get-in state [:searches q])) [[:search/run q] push]
      :else                                  [push])))

(defn feeds->search
  "The search of the feeds that discovery/find-feeds! found at a link, the
  map `found`. Only the podcasts count, not e.g. the feed of a blog."
  [{:keys [feeds] :as found}]
  {:status  :done
   :results (into [] (comp (filter :podcast?) (map feed->result)) feeds)})

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
