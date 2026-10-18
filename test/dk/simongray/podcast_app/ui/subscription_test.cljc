(ns dk.simongray.podcast-app.ui.subscription-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.inbox-test :as inbox-test]
            [dk.simongray.podcast-app.ui.subscription :as subscription]
            [dk.simongray.podcast-app.ui-test :refer [elements]]))

(deftest a-subscription-button
  (let [[_ attrs label] (subscription/button :ready "https://example.org/feed.xml" "A Show")]
    (is (= "Subscribe" label))
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
    (is (= [[:library/unsubscribe "https://example.org/feed.xml" "A Show"]] (get-in attrs [:on :click])))))

(defn chosen
  "The value of the option that the select of where new episodes go, in the
  Hiccup `x`, has selected."
  [x]
  (some #(when (:selected (second %)) (:value (second %))) (elements :option x)))

(deftest where-new-episodes-go
  (let [k inbox-test/k
        x (subscription/new-episodes inbox-test/lib k)]
    (is (= ["The inbox" "The end of the queue" "Next in the queue" "Nowhere"]
           (map last (elements :option x))))
    (is (= "inbox" (chosen x)) "the inbox, by default")
    (is (= [[:library/new-episodes k [:event/target-keyword]]]
           (get-in (first (elements :select x)) [1 :on :change])))
    (is (= "next" (chosen (subscription/new-episodes
                           (library/update-profile inbox-test/lib profile/configure k
                                                   (profile/new-episodes-settings :next))
                           k)))
        "and the show's own choice once it has one")))

(deftest the-state-of-a-subscription
  (let [url "https://example.org/feed.xml"]
    (is (= :waiting (subscription/status {} nil url)) "before the library is restored")
    (is (= :subscribed (subscription/status {} #{(guid/normal-url url)} url)))
    (is (= :subscribing (subscription/status {:subscribing #{url}} #{} url)))
    (is (= :ready (subscription/status {} #{} url)))))
