(ns dk.simongray.elektrolyt.library-test
  (:require [clojure.test :refer [async deftest is]]
            [dk.simongray.like-minded.store :as store]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.inbox-test :as inbox-test]
            [dk.simongray.elektrolyt.library.options :as library-options]
            [dk.simongray.elektrolyt.runtime :as runtime]))

(def at #inst "2026-10-20T12:00:00Z")

(def empty-store
  "What library/read! gives of a store without a library."
  {:library (library/make "phone" {:codec transit/codec}) :version nil})

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
           #js [(library/durable-swap! s empty-store #(app-library/applied-all % [(queued ["f" "a"])])
                                       library-options/options)
                (library/durable-swap! s empty-store #(app-library/applied-all % [(queued ["f" "b"])])
                                       library-options/options)])
          (.then (fn [[a b]]
                   (is (= #{"1" "2"} (set [(:version a) (:version b)])) "one after the other")
                   (library/read! (:library empty-store) s library-options/options)))
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

(deftest a-save-that-failed
  (is (= [5000 10000 20000 300000] (map app-library/retry-ms [1 2 3 9]))
      "is tried again, longer after each failure in a row")
  (let [retries (atom [])
        warned  (atom [])
        state   (atom {})]
    (with-redefs [runtime/later!               (fn [k ms _] (swap! retries conj [k ms]))
                  runtime/cancel!              (fn [k] (swap! retries conj [k :cancelled]))
                  runtime/warn-before-leaving! #(swap! warned conj %)]
      (app-library/retried! state {:error {:id :library/save-failed}})
      (is (= 1 (get-in @state [:save-status :failures])))
      (app-library/retried! state {:error {:id :library/save-failed}})
      (is (= "Your changes aren't saved yet." (:announcement @state)))
      (app-library/retried! state {:version "3"})
      (is (= [[::app-library/retry 5000] [::app-library/retry 10000] [::app-library/retry :cancelled]]
             @retries)
          "until a write goes through")
      (is (nil? (:save-status @state)))
      (is (= "Your changes are saved" (:announcement @state)))
      (is (= [false true false] @warned)
          "while the browser asks before the page goes, from the second failure until then")
      (reset! retries [])
      (app-library/retried! state {:error {:id :library/newer-format}})
      (is (= [[::app-library/retry :cancelled]] @retries)
          "but not after a newer version saved the library, which only a reload can write over"))))

(deftest feeds-from-before-leaned-once
  (let [doubled (update inbox-test/feed :rss/items
                        (partial mapv #(assoc % :content/encoded "<p>Notes</p>" :rss/description "Notes")))
        state   (atom {:library (library/subscribe (library/make "phone" {:config library-options/config})
                                                   inbox-test/url doubled {:at at})})
        saves   (atom 0)]
    (reset! app-library/waiting [])
    (with-redefs [app-library/save! (fn [_] (swap! saves inc))]
      (app-library/leaned! state)
      (is (= [[:library/leaned]] (map :operation @app-library/waiting)))
      (is (= 1 @saves) "and saved, which writes their documents anew")
      (is (every? #(nil? (:rss/description %))
                  (mapcat (comp :rss/items :feed) (vals (:feeds (:library @state))))))
      (app-library/leaned! state)
      (is (= 1 (count @app-library/waiting)) "once, since a lean library stays as it is"))
    (reset! app-library/waiting [])))

(deftest every-new-episode-in-the-inbox
  (async done
    (let [subscribed (-> (library/make "phone" {:config library-options/config})
                         (library/subscribe inbox-test/url inbox-test/feed {:at at}))
          k          (first (keys (:feeds subscribed)))
          ;; as an earlier version of the app, or another app, chose
          queued     (library/update-profile subscribed profile/configure k
                                             (profile/new-episodes-settings :queue))
          newer      (update inbox-test/feed :rss/items
                             #(into [{:rss/title     "Episode 4"
                                      :rss/guid      {:content "ep4"}
                                      :rss/pub-date  #inst "2026-10-04T06:00:00Z"
                                      :rss/enclosure {:url "https://example.org/4.mp3" :type "audio/mpeg"}}]
                                    %))]
      (-> (library/refresh! queued {:at    #inst "2026-10-22T12:00:00Z"
                                    :fetch (fn [u _] {:status 200 :url u :feed newer})
                                    :send  (fn [_] {:status 304 :headers {} :body ""})})
          (.then (fn [{{:keys [profile]} :library}]
                   (is (= [[k "ep4"]] (profile.queue/inbox profile)))
                   (is (= [] (profile.queue/queue profile)) "rather than the queue that the show chose")
                   (is (= :queue (profile/new-episodes (profile/settings profile k)))
                       "whose choice stays in the profile, for the apps that offer it")))
          (.catch #(is false (str "rejected: " %)))
          (.finally done)))))
