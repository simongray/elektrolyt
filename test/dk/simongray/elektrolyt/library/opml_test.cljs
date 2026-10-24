(ns dk.simongray.elektrolyt.library.opml-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.inbox-test :as inbox-test]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.opml :as opml]
            [dk.simongray.elektrolyt.show :as show]))

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
        other   (first (:subscriptions subscription-list))
        fetched (update s :library library/subscribe (:xml-url other) inbox-test/feed)]
    (is (= {:subscriptions [other]} (opml/waiting s subscription-list))
        "those of the shows without their feeds, not the one that the library had")
    (is (nil? (opml/waiting fetched subscription-list)) "until every show has its feed")
    (is (nil? (opml/waiting (update s :library library/unsubscribe
                                    (show/subscribed-key (:library s) (:xml-url other)))
                            subscription-list))
        "or the listener leaves the shows that have none")))

(deftest the-marks-that-wait
  (let [episodes [{:enclosure-url "https://other.example/1.mp3" :played "1"}]]
    (is (= {:subscriptions [{:xml-url "https://other.example/feed.xml" :episodes episodes}]}
           (opml/marked (assoc-in subscription-list [:subscriptions 0 :episodes] episodes)))
        "are those of the shows that mark episodes, with their feed URLs and episodes alone")
    (is (nil? (opml/marked subscription-list)) "or none")))

(deftest each-import-keeps-its-own-marks
  (let [docs     (atom #{})
        db       {:put    (fn [name _ _] (swap! docs conj name) (js/Promise.resolve {}))
                  :delete (fn [name] (swap! docs disj name) (js/Promise.resolve nil))}
        episodes [{:enclosure-url "https://other.example/1.mp3" :played "1"}]
        marks    (opml/marked (assoc-in subscription-list [:subscriptions 0 :episodes] episodes))
        s        (imported {:library inbox-test/lib} subscription-list)
        fetched  (update s :library library/subscribe "https://other.example/feed.xml" inbox-test/feed)]
    ;; with both of change!'s arities, since ClojureScript calls a function
    ;; of several arities at the arity itself
    (with-redefs [opml/marks-db       db
                  app-library/change! (fn ([_ _]) ([_ _ _]))
                  app-library/save!   (fn [_] (js/Promise.resolve nil))]
      (opml/mark! (atom s) "first" marks)
      (opml/mark! (atom s) "second" marks)
      (is (= #{"first" "second"} @docs) "in a document of its own, while a feed is missing")
      (opml/mark! (atom fetched) "first" marks)
      (is (= #{"second"} @docs) "which the other import's marks outlast"))))
