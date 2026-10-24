(ns dk.simongray.elektrolyt.search.directories
  "Running the searches of the Search page: the shows that each directory
  has for words, or the feeds at a link that the listener pasted. The
  search of drpodcast.nu works like a directory's, by the titles in its
  slugs.json."
  (:require [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.runtime.cache :as app-cache]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.search.drpodcast :as drpodcast]))

(def drpodcast-limit
  "The most series that a search finds on drpodcast.nu, since each costs a
  request to DR."
  10)

(defn unpublished!
  "A promise of the `series` of slugs.json if DR publishes no podcast of
  it, or of nil. A series whose check fails counts as unpublished."
  [{:keys [slug] :as series}]
  ;; TODO: with DR's podcastUrl in slugs.json, this wouldn't need a request
  ;; per series, and a series that DR renamed wouldn't count as unpublished.
  ;;
  ;; DR's CDN can ignore the range and send a whole feed, which the
  ;; truncation cuts short
  (-> (app-cache/send {:url       (drpodcast/official-url slug)
                       :range     [0 0]
                       :max-bytes 65536
                       :truncate? true})
      (.then #(when (= 404 (:status %)) series))
      (.catch (constantly series))))

(defn drpodcast-search!
  "A promise of drpodcast.nu's list for the words `q`: the series with
  every word in their titles that DR publishes no podcast of, since the
  other directories list DR's podcasts."
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

(defn directory-list!
  "A promise of the list of the directory `source` for the words `q`, with
  the `index` credentials for the Podcast Index, or of a failed list when
  the directory fails."
  [q source index]
  (-> (if (= drpodcast/source source)
        (drpodcast-search! q)
        (.then (directory/search! q {:send        app-cache/send
                                     :sources     [source]
                                     :credentials index})
               search/shows->list))
      (.catch #(search/failed (ex-message %)))))

(defn search-directory!
  "Search the directory `source` for the words `q` with the `index`
  credentials, and put its list in the :searches of the state through
  `dispatch`.

  A fresh cached list answers at once, unless `retry?`. A new list is
  cached, and for a directory that fails, the cached list stands in with
  its date."
  [dispatch q source index retry?]
  (let [run     (random-uuid)
        listed! #(dispatch [[:search/listed q source run %]])
        k       (search/cache-key source q)
        ask!    (fn [kept]
                  (.then (directory-list! q source index)
                         (fn [l]
                           (if (= :failed (:status l))
                             (listed! (search/stale-list l kept))
                             (do (listed! l)
                                 (app-cache/put! app-cache/searches k l))))))]
    (dispatch [[:store/assoc-in [:searches q :runs source] run]])
    (listed! {:status :searching})
    (.then (app-cache/fetch! app-cache/searches k)
           (fn [kept]
             (if (and (not retry?) (search/fresh? kept (js/Date.)))
               (listed! (:value kept))
               (ask! kept))))))

;; Each directory gets a list of its own, so that no answer reorders or
;; replaces another's, as one merged list did (web.dev, Cumulative Layout
;; Shift)
;; https://web.dev/articles/cls
(defn search!
  "Search for the query `q` and put what's found in the :searches of the
  state through `dispatch`: the feeds at a link, or else a list from each
  of the directories `sources`, with the `index` credentials for the
  Podcast Index."
  [dispatch q sources index]
  (if (search/link? q)
    (let [run    (random-uuid)
          found! #(dispatch [[:search/found q run %]])]
      (dispatch [[:store/assoc-in [:searches q] {:status :searching :results [] :run run}]])
      (-> (discovery/find-feeds! q {:send app-library/send :index index})
          (.then #(found! (search/feeds->search %)))
          (.catch #(found! {:status :done :results [] :error {:message (ex-message %)}}))))
    (do (dispatch [[:store/assoc-in [:searches q] (search/searching sources)]])
        (doseq [source sources]
          (search-directory! dispatch q source index false)))))
