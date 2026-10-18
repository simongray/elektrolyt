(ns dk.simongray.podcast-app.opml-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.inbox-test :as inbox-test]
            [dk.simongray.podcast-app.opml :as opml]))

(def subscription-list
  "A subscription list, as format.opml/parse reads it, of a new show and
  the one that the library has."
  {:title         "Mine"
   :subscriptions [{:type "rss" :text "Other" :xml-url "https://other.example/feed.xml"}
                   {:type "rss" :text "A Show" :xml-url inbox-test/url}]})

(deftest importing-subscriptions
  (let [s (opml/imported {:library inbox-test/lib} subscription-list)]
    (is (= 2 (opml/subscribed-count s)))
    (is (= "Subscribed to 1 show from the file. Their episodes are on the way."
           (get-in s [:settings-status :text]))
        "the new ones counted")
    (is (= "You subscribe to every show of the file already."
           (get-in (opml/imported s subscription-list) [:settings-status :text])))))
