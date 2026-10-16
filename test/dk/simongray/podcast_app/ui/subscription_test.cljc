(ns dk.simongray.podcast-app.ui.subscription-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast-app.ui.subscription :as subscription]))

(deftest a-subscription-button
  (let [[_ attrs label] (subscription/button :ready "https://example.org/feed.xml" "A Show")]
    (is (= "Subscribe" label))
    (is (nil? (:aria-disabled attrs)))
    (is (= [[:library/subscribe "https://example.org/feed.xml" "A Show"]] (get-in attrs [:on :click]))))
  (let [[_ attrs label] (subscription/button :subscribing "https://example.org/feed.xml" "A Show")]
    (is (= "Subscribing…" label))
    (is (= "true" (:aria-disabled attrs)))
    (is (nil? (:on attrs)) "nothing happens until it's done")))

(deftest the-state-of-a-subscription
  (let [url "https://example.org/feed.xml"]
    (is (= :waiting (subscription/status {} nil url)) "before the library is restored")
    (is (= :subscribed (subscription/status {} #{(guid/normal-url url)} url)))
    (is (= :subscribing (subscription/status {:subscribing #{url}} #{} url)))
    (is (= :ready (subscription/status {} #{} url)))))
