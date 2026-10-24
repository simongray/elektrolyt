(ns dk.simongray.elektrolyt.worker.fetching-test
  (:require [cljs.reader :as reader]
            [clojure.test :refer [async deftest is]]
            [dk.simongray.elektrolyt.worker.fetching :as fetching]))

(defn stand-in
  "A stand-in for the browser's Worker, since Node has none, which adds each
  worker that it makes to the atom `made`, as a map of the :listeners by
  type, the messages :posted to it and whether it's :terminated."
  [made]
  (fn [_]
    (let [listeners  (atom {})
          posted     (atom [])
          terminated (atom false)]
      (swap! made conj {:listeners listeners :posted posted :terminated terminated})
      #js {:addEventListener (fn [type f] (swap! listeners update type conj f))
           :postMessage      #(swap! posted conj (reader/read-string %))
           :terminate        #(reset! terminated true)})))

(defn fire!
  "Call the listeners of `type` on the stand-in worker `w` with the event `e`."
  [w type e]
  (doseq [f (get @(:listeners w) type)]
    (f e)))

(deftest a-worker-that-failed
  (async done
    (let [made (atom [])]
      (set! (.-Worker js/globalThis) (stand-in made))
      (reset! fetching/web-worker nil)
      (let [failed   (fetching/fetch-due! [] {} nil)
            _        (fire! (first @made) "error" #js {:message "The script didn't load"})
            answered (fetching/fetch-due! [] {} nil)
            another  (second @made)]
        (is @(:terminated (first @made)) "stops")
        (is (= 2 (count @made)) "and the next ask starts another")
        (fire! another "message" #js {:data (pr-str {:id    (:id (first @(:posted another)))
                                                     :busy? true})})
        (-> failed
            (.then #(is false "the worker that failed answered")
                   #(is (= "The script didn't load" (ex-message %))
                        "which rejects what waited for the one that failed"))
            (.then (fn [_] answered))
            (.then #(is (= {:busy? true} %) "and answers what's asked of it"))
            (.catch #(is false (str "rejected: " %)))
            (.finally #(do (reset! fetching/web-worker nil)
                           (js-delete js/globalThis "Worker")
                           (done))))))))

(defn item
  "An item of a parsed feed with the guid `id`, the `title` and the `day`."
  [id title day]
  {:rss/guid {:content id} :rss/title title :rss/pub-date (js/Date. (* day 86400000))})

(def ours
  "DR's own feed of a series that DR renamed, with an episode three days
  late."
  {:rss/link  "https://www.dr.dk/lyd/p1/hjernekassen-paa-p1-759510699000"
   :rss/items [(item "o2" "Two" 5)]})

(def theirs
  "drpodcast.nu's feed of the series, with an episode that DR hasn't got."
  {:rss/link  "https://www.dr.dk/lyd/p1/hjernekassen-paa-p1-759510699000"
   :rss/items [(item "d3" "Three" 3) (item "d2" "Two" 2)]})

(defn answering
  "A fetch that answers each URL with the feed that `feeds` has for it, or
  a 404, and adds each request to the atom `asked`."
  [feeds asked]
  (fn [url opts]
    (swap! asked conj [url opts])
    (js/Promise.resolve (if-let [feed (get feeds url)]
                          {:status 200 :feed feed}
                          {:status 404 :error {:id :fetch/unreachable}}))))

(deftest a-feed-of-drs-own
  (async done
    (let [dr     "https://api.dr.dk/podcasts/v1/feeds/hjernekassen-pa-p1.xml"
          asked  (atom [])
          guids  #(mapv (comp :content :rss/guid) (get-in % [:feed :rss/items]))
          fetch  (fn [feeds] (fetching/supplementing (answering feeds asked) {:items 20}))]
      (reset! fetching/known-slugs (js/Promise.resolve {"759510699000" "hjernekassen"}))
      (-> ((fetch {dr ours "https://drpodcast.nu/hjernekassen-pa-p1/feed.xml" theirs}) dr {:etag "x" :send :send})
          (.then (fn [answer]
                   (is (= ["d3" "o2"] (guids answer))
                       "takes the episodes of its sibling under DR's slug")
                   (is (= ["https://drpodcast.nu/hjernekassen-pa-p1/feed.xml" {:send :send :items 20}]
                          (first @asked))
                       "which it fetches beside DR's, without DR's validators and with its own options")))
          (.then (fn [_]
                   (reset! asked [])
                   ((fetch {dr ours "https://drpodcast.nu/hjernekassen/feed.xml" theirs}) dr {})))
          (.then (fn [answer]
                   (is (= ["d3" "o2"] (guids answer)) "or under the slug that slugs.json names")
                   (is (= 3 (count @asked)))))
          (.then (fn [_] ((fetch {dr ours}) dr {})))
          (.then (fn [answer]
                   (is (= ["o2"] (guids answer)) "and stays as it is without a sibling")
                   (is (:dk.simongray.elektrolyt.drpodcast/supplemented (:feed answer))
                       "though marked as fetched with drpodcast.nu's episodes")))
          (.then (fn [_]
                   (reset! asked [])
                   ((fetch {"https://example.com/feed.xml" theirs}) "https://example.com/feed.xml" {})))
          (.then (fn [answer]
                   (is (= ["d3" "d2"] (guids answer)))
                   (is (= 1 (count @asked)) "while any other feed is fetched alone")
                   (is (nil? (:dk.simongray.elektrolyt.drpodcast/supplemented (:feed answer))) "and unmarked")))
          (.catch #(is false (str "rejected: " %)))
          (.finally #(do (reset! fetching/known-slugs nil)
                         (done)))))))
