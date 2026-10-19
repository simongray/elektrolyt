(ns dk.simongray.podcast-app.subscription-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.subscription :as subscription]))

(deftest a-subscription
  (let [url   "https://feeds.podcastindex.org/pc20.xml"
        state (subscription/subscribing {:library (library/make "test")} url)]
    (is (= #{url} (:subscribing state)))
    (testing "whose feed was fetched"
      (let [done (subscription/subscribed state url "Podcasting 2.0" nil)]
        (is (empty? (:subscribing done)))
        (is (search/subscribed? (search/subscribed-urls (:library done)) url))
        (is (= "Subscribed to Podcasting 2.0" (:announcement done)))))
    (testing "whose fetch failed"
      (let [error {:id :fetch/status :message "The server answered 404."}
            done  (subscription/subscribed state url "Podcasting 2.0" {:error error})]
        (is (empty? (:subscribing done)))
        (is (= error (get-in done [:subscribe-errors url])))
        (is (not (search/subscribed? (search/subscribed-urls (:library done)) url)))
        (is (= "Couldn't subscribe to Podcasting 2.0" (:announcement done)))))))
