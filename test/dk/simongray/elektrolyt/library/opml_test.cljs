(ns dk.simongray.elektrolyt.library.opml-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.library.inbox-test :as inbox-test]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.opml :as opml]))

(defn imported [state list]
  (operations/changed state (opml/imported state list)))

(def subscription-list
  "A subscription list, as format.opml/parse reads it, of a new show and
  the one that the library has."
  {:title         "Mine"
   :subscriptions [{:type "rss" :text "Other" :xml-url "https://other.example/feed.xml"}
                   {:type "rss" :text "A Show" :xml-url inbox-test/url}]})

(deftest importing-subscriptions
  (let [s (imported {:library inbox-test/lib} subscription-list)]
    (is (= 2 (opml/subscribed-count s)))
    (is (= "Subscribed to 1 show from the file. Their episodes are on the way."
           (get-in s [:settings-status :text]))
        "the new ones counted")
    (is (= "You subscribe to every show of the file already."
           (get-in (imported s subscription-list) [:settings-status :text])))))

(deftest the-marks-wait-for-the-feeds
  (let [s       (imported {:library inbox-test/lib} subscription-list)
        fetched (update s :library library/subscribe "https://other.example/feed.xml" inbox-test/feed)]
    (is (= 1 (opml/fetched-count s)) "only the show that the library had")
    (is (opml/arrived? s fetched) "so the marks come once another feed has arrived")
    (is (= 2 (opml/fetched-count fetched) (opml/subscribed-count fetched)))
    (is (not (opml/arrived? s (assoc s :refresh-status {:running? false :result {:counts {}}})))
        "and not with a refresh that fetched none, e.g. offline")
    (is (not (opml/arrived? fetched (update fetched :library library/unsubscribe (key (first (:feeds inbox-test/lib)))))))))
