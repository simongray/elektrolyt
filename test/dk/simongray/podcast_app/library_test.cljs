(ns dk.simongray.podcast-app.library-test
  (:require [clojure.test :refer [async deftest is]]
            [dk.simongray.like-minded.store :as store]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast-app.library :as app-library]))

(def at #inst "2026-10-20T12:00:00Z")

(def empty-store
  "What library/read! gives of a store without a library."
  {:library (library/make "phone" {:config {:codec transit/codec}}) :version nil})

(defn queued
  "The operation that puts the episode `id` at the end of the queue."
  [id]
  {:operation [:queue/enqueue id] :opts {:at at}})

(defn queue [lib]
  (profile.queue/queue (:profile lib)))

(deftest two-tabs-that-write-at-once
  (async done
    (let [s (store/memory)]
      (-> (js/Promise.all
           #js [(app-library/durable-swap! s empty-store #(app-library/applied-all % [(queued ["f" "a"])]))
                (app-library/durable-swap! s empty-store #(app-library/applied-all % [(queued ["f" "b"])]))])
          (.then (fn [[a b]]
                   (is (= #{"1" "2"} (set [(:version a) (:version b)])) "one after the other")
                   (library/read! (:library empty-store) s)))
          (.then (fn [{:keys [version] lib :library}]
                   (is (= "2" version))
                   (is (= #{["f" "a"] ["f" "b"]} (set (queue lib)))
                       "and neither lost what the other wrote")))
          (.catch #(is false (str "rejected: " %)))
          (.finally done)))))

(deftest operations-that-wait
  (let [lib (:library empty-store)]
    (is (= [["f" "a"] ["f" "b"]]
           (queue (app-library/applied-all lib [(queued ["f" "a"]) (queued ["f" "b"])])))
        "applied in order")
    (is (= [["f" "a"]]
           (queue (app-library/applied-all
                   lib
                   [(queued ["f" "a"])
                    {:operation [:library/subscribe "https://example.org/gone.xml" {:status 404}]
                     :opts      {:at at}}])))
        "but for one that throws, e.g. a subscription to a feed that failed")))
