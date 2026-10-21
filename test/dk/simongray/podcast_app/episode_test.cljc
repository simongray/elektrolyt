(ns dk.simongray.podcast-app.episode-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.show :as show]))

(def url "https://example.org/feed.xml")

(def items
  [{:rss/title       "Episode 2: The Present"
    :rss/guid        {:content "https://example.org/?p=2"}
    :rss/enclosure   {:url "https://example.org/2.mp3" :type "audio/mpeg"}
    :itunes/duration 3600
    :rss/description "<h1>Links</h1><p>At 12:34 we start.</p>"}
   {:rss/title     "Episode 1: The Past"
    :rss/guid      {:content "https://example.org/?p=1"}
    :rss/enclosure {:url "https://example.org/1.mp3" :type "audio/mpeg"}}])

(def feed
  {:rss/title "A Show" :rss/items items})

(def g
  "The guid of the show, as its URL gives it."
  (guid/from-url url))

(def hash-2
  (guid/short-hash "https://example.org/?p=2"))

(defn time-link
  "A time of the show notes as a test sees it."
  [page seconds text]
  [:a {:href (str (:href page) "&t=" seconds)} text])

(defn loaded
  "The state of a page of the show g whose feed the Show page loaded, the
  start of it or, when `whole?`, all of it."
  [whole?]
  {:route {:path-params {:guid g} :query-params {:url url}}
   :feeds {url {:feed feed :whole? whole?}}})

(deftest the-item-that-a-link-names
  (is (= (first items) (episode/item-of items hash-2 nil)))
  (is (= (second items) (episode/item-of items "" "episode-1-the-past"))
      "of two, the one whose title gives the slug")
  (is (= (first items) (episode/item-of items "" "another-slug")) "or else the first")
  (is (nil? (episode/item-of items "0000000" nil))))

(deftest where-the-feed-comes-from
  (is (= {:feed feed :url url :whole? false} (show/source (loaded false) g)))
  (is (= {:feed feed :url url :whole? true} (show/source (loaded true) g)))
  (let [lib (library/subscribe (library/make "test") url {:feed feed})
        k   (first (keys (:feeds lib)))]
    (is (= [k url true] ((juxt :key :url :whole?) (show/source {:library lib :route {:query-params {}}} k)))
        "the library's, under its key"))
  (is (nil? (show/source {} g)))
  (is (nil? (episode/page {:route (:route (loaded false))} g hash-2 nil time-link))
      "and no page before the feed has arrived"))

(deftest the-page-of-an-episode
  (let [state (loaded false)
        page  (episode/page state g hash-2 nil time-link)]
    (is (= "Episode 2: The Present" (:title page)))
    (is (= [g "https://example.org/?p=2"] (:id page)))
    (is (= {:title "A Show" :href (str "/shows/" g "/a-show?url=https%3A%2F%2Fexample.org%2Ffeed.xml")}
           (:show page)))
    (is (= (str "/shows/" g "/episodes/" hash-2 "/episode-2-the-present?url=https%3A%2F%2Fexample.org%2Ffeed.xml")
           (:href page)))
    (is (= {:id (:id page) :guid g :url url :item-guid "https://example.org/?p=2"}
           (episode/playable page))
        "what the player needs to add it to the library")
    (is (= "https://example.org/2.mp3" (:src page)))
    (is (= [[:h3 {} "Links"] [:p {} "At " [:a {:href (str (:href page) "&t=754")} "12:34"] " we start."]]
           (:notes page))
        "the headings of the notes under the page's own, and their times as the view makes them")
    (is (identical? page (episode/page state g hash-2 nil time-link))
        "made once for the same feed")))

(deftest what-the-page-takes-of-the-episode-and-its-show
  (let [fed  (assoc feed
                    :podcast/license   {:content "CC-BY-4.0"}
                    :podcast/people    [{:content "Adam"}]
                    :podcast/locations [{:content "Austin"}]
                    :rss/items         [(assoc (first items)
                                               :podcast/locations [{:content "Birmingham"}]
                                               :itunes/season 1)
                                        (assoc (second items) :itunes/season 1)])
        page (episode/page-of g {:feed fed :url url} (first (:rss/items fed)) time-link)]
    (is (= ["Birmingham"] (map :content (:locations page))) "its own location")
    (is (nil? (:license page)) "but not its show's license, which the show's page shows")
    (is (= ["Adam"] (map :name (:people page))) "and its show's people, when it names none")
    (is (= "https://example.org/?p=2" (:link page)) "its web page, here its guid")
    (is (= {:type "full"} (:numbering page)) "and no season, where the show has only one")
    (is (= 1 (get-in (episode/page-of g {:feed (assoc-in fed [:rss/items 1 :itunes/season] 2) :url url}
                                      (first (:rss/items fed)) time-link)
                     [:numbering :season]))
        "but its season where the show has two")))

(deftest while-there-is-no-episode
  (let [on-its-way {:route (:route (loaded false))}]
    (is (= :loading (episode/status (loaded false) g)) "the whole feed may have it")
    (is (= :not-found (episode/status (loaded true) g)))
    (is (= :loading (episode/status (show/requested (loaded true) [:check url]) g))
        "but a feed from the cache that's fetched again may have it")
    (is (= :loading (episode/status (show/requested on-its-way [:open url]) g)) "as may the cache")
    (is (= :looking-up (episode/status (show/requested {} [:look-up g]) g)))
    (is (= :failed (episode/status (assoc-in on-its-way [:feeds url :error] {:message "No"}) g)))
    (is (= :no-show (episode/status {:feed-urls {g {:not-found? true}}} g)) "the index doesn't know it")
    (is (= :no-show (episode/status {} g)) "nothing says where its feed is")))

(deftest the-chapters-to-list
  (let [doc  {:chapters [{:start-time 0 :title "Intro"} {:start-time 60 :title "Hidden" :toc false}]}
        page {:view {:chapters {:source :embedded :document doc}}}]
    (is (= ["Intro"] (map :title (episode/shown-chapters page nil))) "the feed's, but the hidden ones")
    (is (= ["Read"] (map :title (episode/shown-chapters page {:result {:chapters {:source :file :document {:chapters [{:start-time 0 :title "Read"}]}}}})))
        "the file's, once it's read")
    (is (nil? (episode/shown-chapters {:view {:chapters {:source :inferred :document doc}}} nil))
        "none that the show notes imply, since the notes have their times")))
