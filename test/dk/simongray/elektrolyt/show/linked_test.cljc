(ns dk.simongray.elektrolyt.show.linked-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.show.linked :as linked]))

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
  (is (nil? (linked/card {:title "Nowhere"})) "and none for an item that names no show")
  (is (= [{:href "https://example.org/300.jpg" :width 300} {:href "https://example.org/3000.jpg" :width 3000}]
         (:artwork-sizes (linked/card {:feed-guid "g1"
                                       :show      (assoc show :podcast/images
                                                         [{:href "https://example.org/3000.jpg" :width 3000}
                                                          {:href "https://example.org/300.jpg" :width 300}])})))
      "with the sizes of its artwork, narrowest first, for a srcset"))

(deftest a-fetch-that-failed
  (is (not (linked/failed? {:podroll [{:feed-guid "g1" :show show}] :publisher {:feed {} :shows []}})))
  (is (not (linked/failed? {})) "nothing to fetch")
  (is (linked/failed? {:podroll [{:feed-guid "g1" :show show} {:feed-guid "g2" :error {:status 404}}]})
      "a show of the podroll")
  (is (linked/failed? {:publisher {:feed-url "https://example.org/pub.xml" :error {:status 500}}})
      "the publisher's feed"))

(deftest what-the-page-keeps
  (let [fetched {:podroll   [{:feed-guid "g1" :show show}]
                 :publisher {:feed       {:rss/title "Podnews LLC"}
                             :shows      [{:feed-guid "this"} {:feed-guid "g3" :title "Podnews Daily"}]
                             :confirmed? true}}]
    (is (= {:podroll   [{:guid "g1" :title "Podnews Weekly Review" :url "https://example.org/weekly.xml"
                         :artwork "https://example.org/art.jpg"}]
            :publisher {:title "Podnews LLC" :shows [{:guid "g3" :title "Podnews Daily"}]}}
           (linked/kept #{"this"} fetched))
        "the other shows of the publisher")
    (is (= [{:guid "g3" :title "Podnews Daily"}]
           (get-in (linked/kept #{"from-its-url" "this"} fetched) [:publisher :shows]))
        "and not this show by the guid that its feed declares, where its page goes by another")
    (is (nil? (:publisher (linked/kept #{"this"} (assoc-in fetched [:publisher :confirmed?] false))))
        "but only when its feed lists this show too")))

(deftest a-feed-that-links-to-shows
  (is (linked/links? {:podcast/podroll {:remote-items [{:feed-guid "g1"}]}}))
  (is (not (linked/links? {:rss/title "Alone"}))))
