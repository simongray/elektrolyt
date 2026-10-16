(ns dk.simongray.podcast-app.show-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.show :as show]))

(def url "https://example.org/feed.xml")

(defn feed
  "A feed titled `title` with an episode for each of the `episodes`."
  [title & episodes]
  {:rss/title title
   :rss/items (vec (for [e episodes]
                     {:rss/title e :rss/guid {:content e}}))})

(deftest the-page-from-a-feed
  (let [page (show/feed->page url (feed "A Show" "One" "Two"))]
    (is (= "A Show" (:title page)))
    (is (= #{"One" "Two"} (set (map :title (:episodes page)))))
    (is (every? :guid (:episodes page)) "each row has the key of its list item")))

(deftest a-feed-that-arrives-in-stages
  (let [guid  "g"
        state {:shows {guid {:url url :status :loading}}}
        head  (show/arrived state guid url (feed "A Show" "One") false)
        whole (show/arrived head guid url (feed "A Show" "One" "Two") true)]
    (testing "the start of the feed, while the whole is fetched"
      (is (= :loading (get-in head [:shows guid :status])))
      (is (= ["One"] (map :title (get-in head [:shows guid :page :episodes]))))
      (is (nil? (get-in head [:shows guid :feed]))))
    (testing "the whole feed, which a subscription can take"
      (is (= :done (get-in whole [:shows guid :status])))
      (is (= 2 (count (get-in whole [:shows guid :page :episodes]))))
      (is (some? (get-in whole [:shows guid :feed]))))
    (is (= whole (show/arrived whole guid url (feed "A Show" "One") false))
        "a start that arrives after the whole feed changes nothing")
    (is (= :previewed (get-in (show/arrived {} guid url (feed "A Show") false) [:shows guid :status]))
        "a start that a pointer on a link asked for")))

(deftest the-artwork-of-a-search-in-its-sizes
  (let [guid  "g"
        sizes [{:href "https://i/100.jpg" :width 100} {:href "https://i/600.jpg" :width 600}]
        state {:searches {"q" {:results [{:guid guid :url url :title "A Show"
                                          :artwork "https://i/600.jpg" :artwork-sizes sizes}]}}}
        fed   (-> (feed "A Show" "One")
                  (assoc :itunes/image {:href "https://i/3000.jpg"}))
        page  (get-in (show/arrived state guid url fed false) [:shows guid :page])]
    (is (= "https://i/600.jpg" (:artwork page)) "rather than the feed's one size")
    (is (= sizes (:artwork-sizes page)))))

(deftest the-best-source-of-a-page
  (let [guid  "917393e3-1b1e-5cef-ace4-edaa54e1f810"
        found {:searches {"pc20" {:results [{:guid guid :title "From the search" :url url}]}}}]
    (is (= "From the search" (:title (show/page found guid))) "what a search knew, at once")
    (is (= [] (:episodes (show/page found guid))))
    (is (= "From the search"
           (:title (show/page (assoc-in found [:shows guid] {:url url :status :loading}) guid)))
        "and while the feed is on its way")
    (is (= "From the feed"
           (:title (show/page (assoc-in found [:shows guid] {:url url :page {:title "From the feed"}}) guid)))
        "the feed once it has arrived")
    (is (nil? (show/page {} guid)) "a show that nothing knows")))

(deftest a-show-that-the-library-keeps
  (let [lib (library/subscribe (library/make "test") url (feed "Kept" "One"))
        k   (key (first (library/subscriptions lib)))]
    (is (= k (show/library-key lib k nil)) "by its key")
    (is (= k (show/library-key lib "another-guid" url)) "or by the URL of its feed")
    (is (nil? (show/library-key lib "another-guid" "https://example.org/other.xml")))
    (is (= "Kept" (:title (show/page {:library lib} k))))
    (is (= url (show/feed-url {:library lib} k)))))
