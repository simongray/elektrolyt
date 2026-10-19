(ns dk.simongray.podcast-app.ui.subscription-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast-app.ui.subscription :as subscription]))

(deftest a-subscription-button
  (let [[_ attrs label] (subscription/button :ready "https://example.org/feed.xml" "A Show")]
    (is (= "Subscribe" label))
    (is (= "primary" (:class attrs)) "filled, as the main action")
    (is (nil? (:aria-disabled attrs)))
    (is (= [[:library/subscribe "https://example.org/feed.xml" "A Show"]] (get-in attrs [:on :click]))))
  (let [[_ attrs label] (subscription/button :subscribing "https://example.org/feed.xml" "A Show")]
    (is (= "Subscribing…" label))
    (is (= "true" (:aria-disabled attrs)))
    (is (nil? (:on attrs)) "nothing happens until it's done"))
  (let [[_ attrs label] (subscription/button :subscribed "https://example.org/feed.xml" "A Show")]
    (is (= "Subscribed" label) "where a page offers no way out")
    (is (= "true" (:aria-disabled attrs))))
  (let [[_ attrs label] (subscription/button :subscribed "https://example.org/feed.xml" "A Show" true)]
    (is (= "Unsubscribe" label) "and where it does")
    (is (= [[:library/unsubscribe "https://example.org/feed.xml" "A Show"]] (get-in attrs [:on :click])))
    (is (nil? (:class attrs)) "which isn't filled")))

(deftest the-state-of-a-subscription
  (let [url "https://example.org/feed.xml"]
    (is (= :waiting (subscription/status {} nil url)) "before the library is restored")
    (is (= :subscribed (subscription/status {} #{(guid/normal-url url)} url)))
    (is (= :subscribing (subscription/status {:subscribing #{url}} #{} url)))
    (is (= :ready (subscription/status {} #{} url)))))
