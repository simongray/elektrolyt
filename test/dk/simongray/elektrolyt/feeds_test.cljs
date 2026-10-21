(ns dk.simongray.elektrolyt.feeds-test
  (:require [clojure.test :refer [async deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.feeds :as feeds]
            [dk.simongray.elektrolyt.fetching :as fetching]))

(deftest a-subscription-whose-fetch-failed
  (async done
    (let [url   "https://example.org/feed.xml"
          store (atom {:library (library/make "test")})]
      (with-redefs [fetching/fetch! (fn [_ _] (js/Promise.reject (js/Error. "The worker failed")))]
        (-> (feeds/subscribe! store url "A Show")
            (.then (fn [_]
                     (is (empty? (:subscribing @store)) "isn't under way any longer")
                     (is (= {:message "The worker failed"} (get-in @store [:subscribe-errors url]))
                         "but failed, as a fetch that answers with an error does")
                     (is (= "Couldn't subscribe to A Show" (:announcement @store)))))
            (.catch #(is false (str "rejected: " %)))
            (.finally done))))))
