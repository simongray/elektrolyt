(ns dk.simongray.podcast-app.episode-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast-app.episode :as episode]))

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
  "The state of a page whose show the Show page loaded, the start of its
  feed or, when `whole?`, all of it."
  [whole? status]
  {:route {:query-params {:url url}}
   :shows {g (cond-> {:url url :status status}
               whole?       (assoc :feed feed)
               (not whole?) (assoc :head feed))}})

(deftest the-item-that-a-link-names
  (is (= (first items) (episode/item-of items hash-2 nil)))
  (is (= (second items) (episode/item-of items "" "episode-1-the-past"))
      "of two, the one whose title gives the slug")
  (is (= (first items) (episode/item-of items "" "another-slug")) "or else the first")
  (is (nil? (episode/item-of items "0000000" nil))))

(deftest where-the-feed-comes-from
  (is (= {:feed feed :url url :whole? false} (episode/source (loaded false :loading) g)))
  (is (= {:feed feed :url url :whole? true} (episode/source (loaded true :done) g)))
  (let [lib (library/subscribe (library/make "test") url {:feed feed})
        k   (first (keys (:feeds lib)))]
    (is (= [k url true] ((juxt :key :url :whole?) (episode/source {:library lib :route {:query-params {}}} k)))
        "the library's, under its key"))
  (is (nil? (episode/source {} g))))

(deftest the-page-of-an-episode
  (let [state (loaded false :loading)
        page  (episode/page state g hash-2 nil time-link)]
    (is (= "Episode 2: The Present" (:title page)))
    (is (= [g "https://example.org/?p=2"] (:id page)))
    (is (= {:title "A Show" :href (str "/shows/" g "/a-show?url=https%3A%2F%2Fexample.org%2Ffeed.xml")}
           (:show page)))
    (is (= (str "/shows/" g "/episodes/" hash-2 "/episode-2-the-present?url=https%3A%2F%2Fexample.org%2Ffeed.xml")
           (:href page)))
    (is (= {:id (:id page) :src "https://example.org/2.mp3" :title "Episode 2: The Present" :show "A Show"}
           (episode/playable page)))
    (is (= [[:h3 {} "Links"] [:p {} "At " [:a {:href (str (:href page) "&t=754")} "12:34"] " we start."]]
           (:notes page))
        "the headings of the notes under the page's own, and their times as the view makes them")
    (is (identical? page (episode/page state g hash-2 nil time-link))
        "made once for the same feed")))

(deftest while-there-is-no-episode
  (is (= :loading (episode/status (loaded false :loading) g)) "the whole feed may have it")
  (is (= :not-found (episode/status (loaded true :done) g)))
  (is (= :looking-up (episode/status {:shows {g {:status :looking-up}}} g)))
  (is (= :failed (episode/status (loaded false :failed) g)))
  (is (= :no-show (episode/status {:shows {g {:status :not-found}}} g)) "the index doesn't know it")
  (is (= :no-show (episode/status {} g)) "nothing says where its feed is"))

(deftest the-chapters-to-list
  (let [doc  {:chapters [{:start-time 0 :title "Intro"} {:start-time 60 :title "Hidden" :toc false}]}
        page {:view {:chapters {:source :embedded :document doc}}}]
    (is (= ["Intro"] (map :title (episode/shown-chapters page nil))) "the feed's, but the hidden ones")
    (is (= ["Read"] (map :title (episode/shown-chapters page {:result {:chapters {:source :file :document {:chapters [{:start-time 0 :title "Read"}]}}}})))
        "the file's, once it's read")
    (is (nil? (episode/shown-chapters {:view {:chapters {:source :inferred :document doc}}} nil))
        "none that the show notes imply, since the notes have their times")))

(deftest the-paragraphs-of-a-transcript
  (testing "a speaker's turn"
    (is (= [{:start-time 0.0 :speaker "Adam" :body "Hello there. How are you?"}
            {:start-time 3.0 :speaker "Dave" :body "Fine."}]
           (map #(select-keys % [:start-time :speaker :body])
                (episode/paragraphs {:segments [{:start-time 0.0 :end-time 1.0 :speaker "Adam" :body "Hello there."}
                                                {:start-time 1.0 :end-time 3.0 :speaker "Adam" :body "How are you?"}
                                                {:start-time 3.0 :end-time 4.0 :speaker "Dave" :body "Fine."}]})))))
  (testing "about a minute of a transcript without speakers"
    (is (= [[0.0 "One two"] [61.0 "three"]]
           (map (juxt :start-time :body)
                (episode/paragraphs {:segments [{:start-time 0.0 :body "One"}
                                                {:start-time 30.0 :body "two"}
                                                {:start-time 61.0 :body "three"}]}))))))
