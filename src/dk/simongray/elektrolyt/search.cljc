(ns dk.simongray.elektrolyt.search
  "What the Search page finds for a query: the shows that each directory
  has for words, or the feeds at a link that the listener pasted.

  A search of words is a map of the :sources it asks, in the listener's
  order, their :lists by source, and the source whose list the listener
  last jumped to, :shown. Each list has its :status, :searching, :done or
  :failed, its :results and, if it failed, the :reason. A search of a link
  has a :status, :results and, if it failed, an :error.

  Each directory's list stays in its own order, unchanged by the others.
  A cached list less than a day old answers without asking the directory,
  and an older one stands in for a directory that fails."
  (:require [clojure.string :as str]
            [dk.simongray.like-minded.cache :as cache]
            [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.search.drpodcast :as drpodcast]))

(defn link?
  "Whether the query `q` is a link, e.g. a feed URL, a domain, a web page
  or an app's link, rather than words for the directories."
  [q]
  (not (:error (discovery/read-input q))))

(defn result
  "The result for `x`, a directory's show or a feed, with its feed at `url`
  and the guid that the feed declares, or else one from the URL."
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
  "The result for the feed `found` at a link, with the title of the link's
  page where the feed has none."
  [{:keys [url title feed] :as found}]
  (update (result feed url) :title #(or % title)))

(defn index-credentials
  "The Podcast Index credentials for podcast-clj in `state`: an empty map,
  since the app's server signs the requests, or nil when it can't."
  [state]
  (when (get-in state [:server :podcastindex?])
    {}))

(defn sources
  "The directories that a search asks in `state`, in the listener's order,
  with the Podcast Index only when the app's server signs requests to it,
  and drpodcast.nu last unless the listener turned it off."
  [state]
  (let [index? (get-in state [:server :podcastindex?])]
    (cond-> (filterv #(or index? (not= :podcastindex %)) (settings/searched state))
      (settings/alternative-feeds? state) (conj drpodcast/source))))

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
  "The list of one directory from its search `answer`: :done with its
  :results, or failed with the :reason."
  [{:keys [results report] :as answer}]
  (if-let [{:keys [reason]} (first report)]
    (failed reason)
    {:status :done :results (into [] (keep show->result) results)}))

(defn cache-key
  "The cache key of the list of the directory `source` for the words `q`,
  ignoring case and spacing, as the directories do."
  [source q]
  [source (str/lower-case (str/join " " (str/split (str/trim q) #"\s+")))])

;; Apple's Search API has its answers cached for a day, by their max-age
;; https://performance-partners.apple.com/search-api
(defn fresh?
  "Whether the cached list `kept` is fresh enough at the instant `at` to
  answer a search without asking the directory, i.e. less than a day old."
  [kept at]
  (boolean (and kept (< (cache/age kept at) 86400))))

;; RFC 5861, section 4: stale-if-error
(defn stale-list
  "The list `l` of a directory that didn't answer, with the results of its
  cached list `kept` and their :kept-at, if it has any."
  [l kept]
  (if (seq (get-in kept [:value :results]))
    (assoc l :results (get-in kept [:value :results]) :kept-at (:at kept))
    l))

(defn failed?
  "Whether the `search` failed, as a whole or in one of its directories."
  [{:keys [error lists] :as search}]
  (boolean (or error (some #(= :failed (:status %)) (vals lists)))))

(defn results-of
  "Every result of the `search`, of a link or of the directories in the
  order of their lists."
  [{:keys [results sources lists] :as search}]
  (or results
      (into [] (mapcat #(get-in lists [% :results])) sources)))

(defn searching?
  "Whether the `search` waits for an answer."
  [{:keys [status lists] :as search}]
  (boolean (or (= :searching status)
               (some #(= :searching (:status %)) (vals lists)))))

(defn outdated?
  "Whether the search of the words `q` in `state` asked other directories
  than a search asks now, e.g. those from before a change in Settings."
  [state q]
  (let [{:keys [lists] :as search} (get-in state [:searches q])]
    (boolean (and lists (not= (:sources search) (sources state))))))

(defn submitted
  "The actions for the words or the link `q` that the listener submitted
  in `state`: put the query in the URL, whose navigation runs a search
  that isn't in the state yet, and first rerun a search that failed
  somewhere or asked other directories.

  A search that went well stays, since the cache would return the same
  lists. One that runs again comes first, so that the navigation finds it
  under way and doesn't start another."
  [state q]
  (let [q      (str/trim (str q))
        push   [:route/push :route/search {} {:q q}]
        rerun? (or (failed? (get-in state [:searches q])) (outdated? state q))]
    (cond
      (str/blank? q) [[:route/push :route/search]]
      rerun?         [[:search/run q] push]
      :else          [push])))

;; A search fails when the link couldn't be fetched, e.g. offline, so that
;; it can run again. A link without feeds is an answer, not a failure.
(defn feeds->search
  "The search of the feeds `found` at a link, counting only podcasts, not
  e.g. a blog's feed. It has the :error of a link that couldn't be
  fetched."
  [{:keys [feeds error] :as found}]
  (cond-> {:status  :done
           :results (into [] (comp (filter :podcast?) (map feed->result)) feeds)}
    (#{:fetch/unreachable :fetch/status} (:id error)) (assoc :error error)))

(defn source-name
  "The name of the directory `source-id` for the listener."
  [source-id]
  (if (= drpodcast/source source-id)
    drpodcast/source-name
    (get-in directory/sources [source-id :name] (name source-id))))

(defn subscribed-urls
  "The set of normalized feed URLs of the subscriptions in the library
  `lib`, or nil without a library."
  [lib]
  (when lib
    (into #{}
          (keep #(some-> % val :url guid/normal-url))
          (profile/subscriptions (:profile lib)))))

(defn subscribed?
  "Whether the feed at `url` is among the normalized `subscribed` URLs."
  [subscribed url]
  (contains? subscribed (guid/normal-url url)))

(comment
  (link? "podcasting 2.0")
  ;; => false
  (link? "atp.fm")
  ;; => true
  #_.)
