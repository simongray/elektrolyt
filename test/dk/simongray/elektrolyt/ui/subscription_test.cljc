(ns dk.simongray.elektrolyt.ui.subscription-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.subscription :as subscription]))

(deftest a-subscription-button
  (let [[tag attrs label] (subscription/button :ready "https://example.org/feed.xml" "A Show")]
    (is (= controls/button tag) "with an icon before its words")
    (is (= ["Subscribe" icons/plus] [label (:icon attrs)]))
    (is (= "primary" (:class attrs)) "filled, as the main action")
    (is (nil? (:aria-disabled attrs)))
    (is (= (controls/single-click [[:library/subscribe "https://example.org/feed.xml" "A Show"]])
           (get-in attrs [:on :click]))
        "once for a double click"))
  (let [[_ attrs label] (subscription/button :subscribing "https://example.org/feed.xml" "A Show")]
    (is (= ["Subscribing…" icons/plus] [label (:icon attrs)]))
    (is (= "true" (:aria-disabled attrs)))
    (is (nil? (:on attrs)) "nothing happens until it's done"))
  (let [[_ attrs label] (subscription/button :subscribed "https://example.org/feed.xml" "A Show")]
    (is (= ["Subscribed" icons/check] [label (:icon attrs)]) "where a page offers no way out")
    (is (= "true" (:aria-disabled attrs))))
  (let [[_ attrs label] (subscription/button :subscribed "https://example.org/feed.xml" "A Show" true)]
    (is (= ["Unsubscribe" icons/minus] [label (:icon attrs)]) "and where it does")
    (is (= (controls/single-click [[:library/unsubscribe "https://example.org/feed.xml" "A Show"]])
           (get-in attrs [:on :click])))
    (is (nil? (:class attrs)) "which isn't filled")))

(deftest the-state-of-a-subscription
  (let [url "https://example.org/feed.xml"]
    (is (= :waiting (subscription/status {} nil url)) "before the library is restored")
    (is (= :subscribed (subscription/status {} #{(guid/normal-url url)} url)))
    (is (= :subscribing (subscription/status {:subscribing #{url}} #{} url)))
    (is (= :ready (subscription/status {} #{} url))))
  (let [old   "https://example.org/old.xml"
        lib   (-> (library/make "test")
                  (library/update-profile profile/subscribe "g" {:url "https://example.org/new.xml"}))
        state {:library lib}]
    (is (= :subscribed (subscription/status state (search/subscribed-urls lib) old "g"))
        "by the show's guid, for a feed that moved since")
    (is (= :ready (subscription/status state (search/subscribed-urls lib) old "other")))))
