(ns dk.simongray.podcast-app.ui.search-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.ui.search :as search]))

(deftest the-status-line
  (is (nil? (search/status-text nil nil)) "before a search")
  (is (= "Searching…"
         (search/status-text "x" {:status :searching :results [{} {}]}))
      "unchanged as the results arrive, so it's read once")
  (is (= "1 show." (search/status-text "x" {:status :done :results [{}]})))
  (is (= "2 shows. fyyd and gpodder.net didn't answer."
         (search/status-text "x" {:status :done :results [{} {}] :failed [:fyyd :gpodder]})))
  (is (= "No shows match “xyzzy”."
         (search/status-text "xyzzy" {:status :done :results []})))
  (is (= "No shows found. Apple Podcasts didn't answer."
         (search/status-text "xyzzy" {:status :done :results [] :failed [:apple]}))
      "no match is certain only when every directory answered")
  (is (= "No podcast feeds at that link."
         (search/status-text "example.org" {:status :done :results []})))
  (is (= "2 podcast feeds."
         (search/status-text "freakshow.fm" {:status :done :results [{} {}]}))
      "the feeds at a link can be those of one show"))

(defn paragraphs
  "The texts of the paragraphs in the Hiccup `x`."
  [x]
  (->> (tree-seq (some-fn vector? seq?) #(if (vector? %) (rest %) %) x)
       (filter #(and (vector? %) (= :p (first %))))
       (map #(apply str (filter string? (rest %))))))

(deftest feeds-that-share-a-title
  (let [found [{:url "https://freakshow.fm/feed/mp3" :title "Freak Show"}
               {:url "https://freakshow.fm/feed/opus" :title "Freak Show"}
               {:url "https://example.org/other.xml" :title "Other"}]
        items (last (search/results {} {:status :done :results found}))]
    (is (= ["https://freakshow.fm/feed/mp3"] (paragraphs (first items))))
    (is (empty? (paragraphs (last items))) "a title of its own needs no URL")))

(deftest a-subscription-button
  (let [[_ attrs label] (search/subscribe-button :ready "https://example.org/feed.xml" "A Show")]
    (is (= "Subscribe" label))
    (is (nil? (:aria-disabled attrs)))
    (is (= [[:library/subscribe "https://example.org/feed.xml" "A Show"]] (get-in attrs [:on :click]))))
  (let [[_ attrs label] (search/subscribe-button :subscribing "https://example.org/feed.xml" "A Show")]
    (is (= "Subscribing…" label))
    (is (= "true" (:aria-disabled attrs)))
    (is (nil? (:on attrs)) "nothing happens until it's done")))
