(ns dk.simongray.podcast-app.linked-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.linked :as linked]))

(def show
  "The start of the feed of a show of a podroll."
  {:rss/title     "Podnews Weekly Review"
   :itunes/image  {:href "https://example.org/art.jpg"}
   :atom/links    [{:rel "self" :href "https://example.org/weekly.xml"}]})

(deftest the-cards-of-the-shows
  (is (= {:guid "g1" :title "Podnews Weekly Review" :url "https://example.org/weekly.xml"
          :artwork "https://example.org/art.jpg"}
         (linked/card {:feed-guid "g1" :feed-url "https://example.org/weekly.xml" :show show}))
      "from the start of its feed")
  (is (= {:guid "g2" :title "Boostagram Ball"}
         (linked/card {:feed-guid "g2" :title "Boostagram Ball" :error {:status 404}}))
      "or from what the podroll says of it, when its feed failed")
  (is (nil? (linked/card {:title "Nowhere"})) "and none for an item that names no show"))

(deftest what-the-page-keeps
  (let [fetched {:podroll   [{:feed-guid "g1" :show show}]
                 :publisher {:feed       {:rss/title "Podnews LLC"}
                             :shows      [{:feed-guid "this"} {:feed-guid "g3" :title "Podnews Daily"}]
                             :confirmed? true}}]
    (is (= {:podroll   [{:guid "g1" :title "Podnews Weekly Review" :url "https://example.org/weekly.xml"
                         :artwork "https://example.org/art.jpg"}]
            :publisher {:title "Podnews LLC" :shows [{:guid "g3" :title "Podnews Daily"}]}}
           (linked/kept "this" fetched))
        "the other shows of the publisher")
    (is (nil? (:publisher (linked/kept "this" (assoc-in fetched [:publisher :confirmed?] false))))
        "but only when its feed lists this show too")))

(deftest a-feed-that-links-to-shows
  (is (linked/links? {:podcast/podroll {:remote-items [{:feed-guid "g1"}]}}))
  (is (not (linked/links? {:rss/title "Alone"}))))
