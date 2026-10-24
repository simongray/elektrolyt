(ns dk.simongray.elektrolyt.search.searches
  "The searches of the Search page: the feeds at a link that the listener
  pasted, or the shows that each directory has for words, from the cache
  while it's fresh. drpodcast.nu answers as a directory too, by the titles
  of its slugs.json.

  The namespace dk.simongray.elektrolyt.search describes what the state
  keeps of each search."
  (:require [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.elektrolyt.cache :as app-cache]
            [dk.simongray.elektrolyt.drpodcast :as drpodcast]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.search :as search]))

(def drpodcast-limit
  "How many series at most a search finds on drpodcast.nu, each of which
  costs a request to DR."
  10)

(defn unpublished!
  "A promise of the `series` of slugs.json when DR publishes no podcast of
  it, or of nil. One that DR wouldn't tell of counts as unpublished."
  [{:keys [slug] :as series}]
  ;; TODO: DR's podcastUrl in slugs.json would tell this without asking DR
  ;; for each series, and would know the feeds of the series that DR
  ;; renamed, which this counts as unpublished.
  ;; DR's CDN can ignore the range and send a whole feed, which the
  ;; truncation cuts short
  (-> (app-cache/send {:url       (drpodcast/official-url slug)
                       :range     [0 0]
                       :max-bytes 65536
                       :truncate? true})
      (.then #(when (= 404 (:status %)) series))
      (.catch (constantly series))))

(defn drpodcast-search!
  "A promise of the list of drpodcast.nu for the words `q`: the series
  whose titles have every word and that DR publishes no podcast of, since
  the other directories list those that it does."
  [q]
  (-> (app-cache/send {:url drpodcast/slugs-url :as :json})
      (.then (fn [{:keys [status body]}]
               (when-not (= 200 status)
                 (throw (js/Error. (str "drpodcast.nu answered " status))))
               (->> (drpodcast/matches body q)
                    (take (* 2 drpodcast-limit))
                    (map unpublished!)
                    into-array
                    js/Promise.all)))
      (.then (fn [found]
               {:status  :done
                :results (into [] (comp (remove nil?) (take drpodcast-limit) (map drpodcast/result))
                               found)}))
      (.catch #(search/failed (ex-message %)))))

(defn search-directory!
  "Search the directory `source` for the words `q`, and keep its list under
  the query in the :searches of the state, through `dispatch`, with the
  `index` credentials for the Podcast Index.

  A list that the cache kept less than a day ago answers at once, unless
  the listener asks to `retry?`. A new list goes into the cache, and for a
  directory that fails, the cache's list shows with its date."
  [dispatch q source index retry?]
  (let [listed! #(dispatch [[:store/assoc-in [:searches q :lists source] %]])
        k       (search/cache-key source q)
        ask!    (fn [kept]
                  (-> (if (= drpodcast/source source)
                        (drpodcast-search! q)
                        (-> (directory/search! q {:send        app-cache/send
                                                  :sources     [source]
                                                  :credentials index})
                            (.then search/shows->list)))
                      (.catch #(search/failed (ex-message %)))
                      (.then (fn [l]
                               (if (= :failed (:status l))
                                 (listed! (search/stale-list l kept))
                                 (do (listed! l)
                                     (app-cache/put! app-cache/searches k l)))))))]
    (listed! {:status :searching})
    (.then (app-cache/fetch! app-cache/searches k)
           (fn [kept]
             (if (and (not retry?) (search/fresh? kept (js/Date.)))
               (listed! (:value kept))
               (ask! kept))))))

;; Each directory answers in a list of its own, so that no answer reorders
;; or replaces what another showed, as one merged list did when the others
;; arrived (web.dev, Cumulative Layout Shift)
;; https://web.dev/articles/cls
(defn search!
  "Search for the query `q`, and keep what's found under the query in the
  :searches of the state, through `dispatch`: the feeds at a link, or else
  the shows that each of the directories `sources` has, in a list of its
  own. With the `index` credentials, the Podcast Index can be among them."
  [dispatch q sources index]
  (if (search/link? q)
    (let [found! #(dispatch [[:store/assoc-in [:searches q] %]])]
      (found! {:status :searching :results []})
      (-> (discovery/find-feeds! q {:send app-library/send :index index})
          (.then #(found! (search/feeds->search %)))
          (.catch #(found! {:status :done :results [] :error (ex-message %)}))))
    (do (dispatch [[:store/assoc-in [:searches q] (search/searching sources)]])
        (doseq [source sources]
          (search-directory! dispatch q source index false)))))
