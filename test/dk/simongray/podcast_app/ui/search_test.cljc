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
  (let [found [{:url "https://freakshow.fm/feed/mp3" :guid "a" :title "Freak Show"}
               {:url "https://freakshow.fm/feed/opus" :guid "b" :title "Freak Show"}
               {:url "https://example.org/other.xml" :guid "c" :title "Other"}]
        items (last (search/results {} {:status :done :results found}))]
    (is (= ["https://freakshow.fm/feed/mp3"] (paragraphs (first items))))
    (is (empty? (paragraphs (last items))) "a title of its own needs no URL")))

(deftest a-link-to-the-page-of-a-show
  (let [[_ {:keys [href on]} title] (search/show-link {:url   "https://feeds.podcastindex.org/pc20.xml"
                                                       :guid  "917393e3-1b1e-5cef-ace4-edaa54e1f810"
                                                       :title "Podcasting 2.0"})]
    (is (= "Podcasting 2.0" title))
    (is (= (str "/shows/917393e3-1b1e-5cef-ace4-edaa54e1f810/podcasting-2-0"
                "?url=https%3A%2F%2Ffeeds.podcastindex.org%2Fpc20.xml")
           href)
        "with the slug of the title, and the feed for a show that the library doesn't keep")
    (is (= [[:show/load "917393e3-1b1e-5cef-ace4-edaa54e1f810" "https://feeds.podcastindex.org/pc20.xml" true]]
           (:pointerdown on))
        "a press fetches the start of the feed at once, before the click")))
