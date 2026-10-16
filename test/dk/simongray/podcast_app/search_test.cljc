(ns dk.simongray.podcast-app.search-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.search :as search]))

(def pc20
  "Podcasting 2.0 as a directory gives it."
  {:rss/title     "Podcasting 2.0"
   :itunes/author "Podcast Index LLC"
   :atom/links    [{:rel "self" :href "https://feeds.podcastindex.org/pc20.xml"}]})

(deftest words-and-links
  (is (not (search/link? "podcasting 2.0")))
  (is (not (search/link? "radiolab")))
  (is (search/link? "atp.fm") "a domain")
  (is (search/link? "https://podcasts.apple.com/us/podcast/id1584274529") "a link of an app"))

(deftest what-the-directories-answered
  (testing "while some directories are yet to answer"
    (is (= {:status  :searching
            :results [{:url     "https://feeds.podcastindex.org/pc20.xml"
                       :title   "Podcasting 2.0"
                       :author  "Podcast Index LLC"
                       :artwork nil}]
            :failed  []}
           (search/shows->search {:results [pc20] :pending [:fyyd]}))))
  (testing "once every directory has answered, some with a failure"
    (let [found (search/shows->search {:results [pc20 {:rss/title "No feed"}]
                                       :report  [{:source :fyyd}]})]
      (is (= :done (:status found)))
      (is (= ["Podcasting 2.0"] (map :title (:results found))) "a show without a feed can't be subscribed to")
      (is (= [:fyyd] (:failed found))))))

(deftest the-podcasts-at-a-link
  (is (= ["https://example.org/podcast.xml"]
         (->> (search/feeds->search {:feeds [{:url "https://example.org/podcast.xml" :podcast? true}
                                             {:url "https://example.org/blog.xml" :podcast? false}]})
              :results
              (map :url)))))

(deftest the-subscribed-feeds
  (let [lib (library/subscribe (library/make "test") "https://feeds.podcastindex.org/pc20.xml")]
    (is (nil? (search/subscribed-urls nil)) "before the library is restored")
    (is (search/subscribed? (search/subscribed-urls lib) "https://feeds.podcastindex.org/pc20.xml"))
    (is (not (search/subscribed? (search/subscribed-urls lib) "https://example.org/other.xml")))))
