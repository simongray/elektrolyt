(ns dk.simongray.podcast-app.opml-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.inbox-test :as inbox-test]
            [dk.simongray.podcast-app.operations :as operations]
            [dk.simongray.podcast-app.opml :as opml]))

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

(defn phases
  "The phases of an import's wait as the store goes through the `states`
  after it, as opml/waited gives them."
  [states]
  (rest (reductions (fn [phase [old new]] (opml/waited phase old new))
                    :waiting
                    (partition 2 1 states))))

(def idle {:refresh-status {:running? false}})
(def running {:refresh-status {:running? true :done 0}})
(def done {:refresh-status {:running? false :result {:counts {}}}})

(deftest the-marks-wait-for-the-feeds
  (is (= [:started :started :fetched] (phases [idle running (assoc-in running [:refresh-status :done] 1) done]))
      "a refresh that started after the import fetches its feeds")
  (is (= [:waiting :started :fetched] (phases [running done running done]))
      "one that was under way already doesn't, but the next one does")
  (is (= [:started :waiting :started :fetched] (phases [idle running idle running done]))
      "nor one that found another tab's worker refreshing, which leaves no result")
  (is (= [:started :waiting] (phases [idle running idle]))
      "so the marks wait for the next refresh, as they do after one that failed"))
