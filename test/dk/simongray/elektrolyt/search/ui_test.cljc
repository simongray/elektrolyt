(ns dk.simongray.elektrolyt.search.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.search :as app-search]
            [dk.simongray.elektrolyt.search.ui :as search]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui-test :refer [elements spoken text-of]]))

(defn listed
  "A search of the directories `sources` for which each of `lists` has
  come, a map by source."
  [sources lists]
  (update (app-search/searching sources) :lists merge lists))

(def shows
  "A list of a directory with `n` shows."
  #(hash-map :status :done :results (vec (for [i (range %)] {:url (str "https://example.org/" i) :title (str "Show " i)}))))

(deftest the-status-line
  (is (nil? (search/status-text nil nil)) "before a search")
  (is (= "Searching…"
         (search/status-text "x" (listed [:apple :fyyd] {:apple (shows 2)})))
      "until every directory has answered, so it's read once")
  (is (= "Found 2 shows in Apple Podcasts and 1 in fyyd."
         (search/status-text "x" (listed [:apple :fyyd] {:apple (shows 2) :fyyd (shows 1)}))))
  (is (= "Found 1 show in Apple Podcasts. fyyd and gpodder.net didn't answer."
         (search/status-text "x" (listed [:apple :fyyd :gpodder]
                                         {:apple   (shows 1)
                                          :fyyd    (app-search/failed "fyyd answered 500")
                                          :gpodder (app-search/failed "timeout")}))))
  (is (= "No shows match “xyzzy”."
         (search/status-text "xyzzy" (listed [:apple :fyyd] {:apple (shows 0) :fyyd (shows 0)}))))
  (is (= "No podcast feeds at that link."
         (search/status-text "example.org" {:status :done :results []})))
  (is (= "2 podcast feeds."
         (search/status-text "freakshow.fm" {:status :done :results [{} {}]}))
      "the feeds at a link can be those of one show")
  (is (= [:p.searching :p :p]
         (map first [(search/status-line "x" (listed [:apple :fyyd] {:apple (shows 2)}))
                     (search/status-line "x" (listed [:apple :fyyd] {:apple (shows 2) :fyyd (shows 1)}))
                     (search/status-line nil nil)]))
      "with a line that keeps its place while it searches")
  (is (= [:span.visually-hidden "Searching…"]
         (last (search/status-line "x" (listed [:apple :fyyd] {:apple (shows 2)}))))
      "though only a screen reader hears it then"))

(defn paragraphs
  "The texts of the paragraphs in the Hiccup `x`."
  [x]
  (map #(apply str (filter string? (rest %))) (elements :p x)))

(deftest feeds-that-share-a-title
  (let [found [{:url "https://freakshow.fm/feed/mp3" :guid "a" :title "Freak Show"}
               {:url "https://freakshow.fm/feed/opus" :guid "b" :title "Freak Show"}
               {:url "https://example.org/other.xml" :guid "c" :title "Other"}]
        items (last (search/results {} :h2 found))]
    (is (= ["https://freakshow.fm/feed/mp3"] (paragraphs (first items))))
    (is (empty? (paragraphs (last items))) "a title of its own needs no URL")))

(deftest a-subscribed-show-among-the-results
  (let [found        [{:url "https://example.org/kept.xml" :guid "a" :title "Kept"}
                      {:url "https://example.org/other.xml" :guid "b" :title "Other"}]
        lib          (library/update-profile (library/make "test") profile/subscribe "a"
                                             {:url "https://example.org/kept.xml"})
        x            (search/results {:library lib} :h3 found)
        [kept other] (elements :h3 x)]
    (is (= " Kept, subscribed" (spoken kept)) "marked before its title")
    (is (= "Other" (spoken other)))
    (is (empty? (concat (elements controls/button x) (elements controls/icon-button x)))
        "with no button, since a show subscribes from its own page")))

(defn page
  "The Search page for the words `q` with the `search`."
  [q search]
  (search/content {:route    {:name :route/search :query-params {:q q}}
                   :searches {q search}}))

(deftest a-list-for-each-directory
  (let [x (page "x" (listed [:fyyd :apple] {:fyyd (shows 3)}))]
    (is (= ["fyyd" "Apple Podcasts"] (map text-of (elements :h2 x))) "in the listener's order")
    (is (= ["Show 0" "Show 1" "Show 2"] (map text-of (elements :h3 x))) "each show under its list")
    (is (re-find #"Searching…" (text-of (second (elements :section.search-list x))))
        "and the list of a directory that is yet to answer says so")
    (testing "the jump row, with the count of each list"
      (let [links (elements :a (elements :nav.search-jump x))]
        (is (= ["fyyd 3 shows" "Apple Podcasts …, searching"] (map text-of links)))
        (is (= [[:event/prevent-default]
                [:store/assoc-in [:searches "x" :shown] :fyyd]
                [:page/show (search/list-id :fyyd)]]
               (get-in (first links) [1 :on :click]))
            "which brings the list into view, and moves the focus to its heading")
        (is (nil? (get-in (first links) [1 :aria-current])))
        (is (= "true" (get-in (first (elements :a (elements :nav.search-jump
                                                          (page "x" (assoc (listed [:fyyd :apple] {:fyyd (shows 3)})
                                                                           :shown :fyyd)))))
                              [1 :aria-current]))
            "and is the current one once it has"))))
  (is (empty? (elements :nav.search-jump (page "x" (listed [:apple] {:apple (shows 1)}))))
      "and none for a single directory"))

(deftest a-list-stays-as-it-appeared
  (let [lists {:apple (shows 2)}
        apple #(first (elements :section.search-list (page "x" (listed [:apple :fyyd] %))))]
    (is (= (apple lists) (apple (assoc lists :fyyd (shows 5))))
        "whatever another directory answers later")))

(deftest a-directory-that-didnt-answer
  (let [x (page "x" (listed [:apple] {:apple (app-search/failed "timeout")}))]
    (is (re-find #"Didn't answer" (text-of (first (elements :div.notice x))))
        "in a notice")
    (is (= [[:search/retry "x" :apple]]
           (get-in (first (elements controls/button (elements :section.search-list x))) [1 :on :click]))
        "which can be asked again")
    (is (= (str "#" (search/list-id :apple))
           (get-in (first (elements controls/button (elements :section.search-list x))) [1 :data-refocus]))
        "and gives the focus to the list's heading as the button goes"))
  (let [kept (app-search/stale-list (app-search/failed "timeout")
                                    {:value {:results [{:url "https://a.example/feed.xml" :title "A"}]}
                                     :at    #inst "2026-10-12T12:00:00Z"})
        x    (page "x" (listed [:apple] {:apple kept}))]
    (is (re-find #"Didn't answer, so these are its shows of \S+.*2026\." (text-of x))
        "with the list that the cache kept, and its date")
    (is (= 1 (count (elements :ol.search-results x))))))

(deftest where-a-search-looks
  (let [x (search/search-form nil [:apple :fyyd])]
    (is (re-find #"Searches Apple Podcasts and fyyd\. Change the directories" (text-of x)))
    (is (= "/settings#search" (get-in (first (elements :a x)) [1 :href]))
        "a link to the Search group of the settings"))
  (let [x (search/search-form nil [:apple :fyyd :drpodcast])]
    (is (re-find #"Searches Apple Podcasts and fyyd\. Change the directories" (text-of x))
        "with drpodcast.nu apart from the directories")
    (is (re-find #"Also searches drpodcast.nu for DR's programmes\. Change" (text-of x)))
    (is (= ["/settings#search" "/settings#dr"] (map #(get-in % [1 :href]) (elements :a x)))
        "since the DR group of the settings turns it on and off")))

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
