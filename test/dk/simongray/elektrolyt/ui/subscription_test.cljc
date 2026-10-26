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
  (let [[tag attrs & words] (subscription/button :ready "https://example.org/feed.xml" "A Show")]
    (is (= controls/icon-button tag) "a bookmark, its words for a screen reader and the tooltip")
    (is (= "bookmark" (:class attrs)) "before the show's title")
    (is (= ["Subscribe to A Show" (subscription/bookmark icons/bookmark-simple-thin)] [(apply str words) (:icon attrs)]))
    (is (nil? (:aria-disabled attrs)))
    (is (= (controls/single-click [[:library/subscribe "https://example.org/feed.xml" "A Show"]])
           (get-in attrs [:on :click]))
        "once for a double click"))
  (let [[_ attrs & words] (subscription/button :subscribing "https://example.org/feed.xml" "A Show")]
    (is (= ["Subscribing to A Show…" (subscription/bookmark subscription/filled-bookmark)] [(apply str words) (:icon attrs)])
        "filled in at once")
    (is (= "true" (:aria-disabled attrs)))
    (is (nil? (:on attrs)) "nothing happens until it's done"))
  (let [[_ attrs & words] (subscription/button :subscribed "https://example.org/feed.xml" "A Show")]
    (is (= ["Unsubscribe from A Show" (subscription/bookmark subscription/filled-bookmark)] [(apply str words) (:icon attrs)])
        "and unsubscribes once it's subscribed")
    (is (= (controls/single-click [[:library/unsubscribe "https://example.org/feed.xml" "A Show"]])
           (get-in attrs [:on :click]))))
  (let [[_ attrs & words] (subscription/button :unsubscribing "https://example.org/feed.xml" "A Show")]
    (is (= ["Unsubscribing from A Show…" (subscription/bookmark icons/bookmark-simple-thin)] [(apply str words) (:icon attrs)])
        "empty at once")
    (is (= "true" (:aria-disabled attrs)))
    (is (nil? (:on attrs)))))

(deftest a-mark-of-a-subscription
  (let [[icon & more] (subscription/marked [:a {:href "/shows/g"} "A Show"])]
    (is (= [controls/icon {:icon icons/bookmark-simple-fill :class "bookmark" :title "Subscribed"}] icon)
        "the filled bookmark before the title, which does nothing")
    (is (= [" " [:a {:href "/shows/g"} "A Show"] [:span.visually-hidden ", subscribed"]] more)
        "and words after it for a screen reader")))

(deftest the-state-of-a-subscription
  (let [url "https://example.org/feed.xml"]
    (is (= :waiting (subscription/status {} nil url)) "before the library is restored")
    (is (= :subscribed (subscription/status {} #{(guid/normal-url url)} url)))
    (is (= :subscribing (subscription/status {:subscribing #{url}} #{} url)))
    (is (= :unsubscribing (subscription/status {:unsubscribing #{url}} #{(guid/normal-url url)} url))
        "while the library still has the show")
    (is (= :ready (subscription/status {} #{} url))))
  (let [old   "https://example.org/old.xml"
        lib   (-> (library/make "test")
                  (library/update-profile profile/subscribe "g" {:url "https://example.org/new.xml"}))
        state {:library lib}]
    (is (= :subscribed (subscription/status state (search/subscribed-urls lib) old "g"))
        "by the show's guid, for a feed that moved since")
    (is (= :ready (subscription/status state (search/subscribed-urls lib) old "other")))))
