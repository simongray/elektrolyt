(ns dk.simongray.elektrolyt.show-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.show :as show]))

(def url "https://example.org/feed.xml")

(defn feed
  "A feed titled `title` with an episode for each of the `episodes`."
  [title & episodes]
  {:rss/title title
   :rss/items (vec (for [e episodes]
                     {:rss/title e :rss/guid {:content e}}))})

(defn on-page
  "The `state` on the Show page of the show `guid`, whose link gives the
  URL of its feed."
  ([guid]
   (on-page {} guid))
  ([state guid]
   (assoc state :route {:name         :route/show
                        :path-params  {:guid guid}
                        :query-params {:url url}})))

(deftest the-page-from-a-feed
  (let [page (show/feed->page url (feed "A Show" "One" "Two"))]
    (is (= "A Show" (:title page)))
    (is (= #{"One" "Two"} (set (map :title (:episodes page)))))
    (is (every? :guid (:episodes page)) "each row has the key of its list item")))

(deftest the-facts-of-a-feed
  (let [dated (fn [title date] {:rss/title title :rss/guid {:content title} :rss/pub-date date})
        fed   {:rss/title       "A Show"
               :rss/link        "https://example.org"
               :rss/language    "en"
               :itunes/explicit true
               :podcast/people  [{:content "Adam"}]
               :rss/items       [(dated "Three" #inst "2026-01-15T00:00:00Z")
                                 (dated "Two" #inst "2026-01-08T00:00:00Z")
                                 (dated "One" #inst "2026-01-01T00:00:00Z")]}
        page  (show/feed->page url fed)]
    (is (= {:link "https://example.org" :language "en" :explicit? true
            :people [{:name "Adam" :role "host" :group "cast"}]}
           (select-keys page [:link :language :explicit? :people])))
    (is (= #inst "2026-01-15T00:00:00Z" (:latest page)))
    (is (= (* 7 24 3600 1000) (:cadence page)) "how often it publishes, up to its latest episode")
    (is (nil? (:cadence (show/feed->page url (feed "A Show" "One")))) "but not without dates")))

(deftest a-feed-that-arrives-in-stages
  (let [guid  "g"
        state (show/requested (on-page guid) [:whole url])
        head  (-> (show/requested state [:head url])
                  (show/answered [:head url])
                  (show/arrived url (feed "A Show" "One")))
        whole (-> (show/answered head [:whole url])
                  (show/whole url {:feed (feed "A Show" "One" "Two")} #inst "2026-10-18T12:00:00Z" true))]
    (testing "the start of the feed, while the whole is fetched"
      (is (= :loading (show/status head guid)))
      (is (= ["One"] (map :title (:episodes (show/page head guid)))))
      (is (false? (:whole? (show/source head guid))))
      (is (nil? (show/whole-feed head url))))
    (testing "the whole feed, which a subscription can take"
      (is (= :done (show/status whole guid)))
      (is (= 2 (count (:episodes (show/page whole guid)))))
      (is (= (feed "A Show" "One" "Two") (:feed (show/whole-feed whole url)))))
    (is (= whole (show/arrived whole url (feed "A Show" "One")))
        "a start that arrives after the whole feed changes nothing")
    (is (= {:feed (feed "A Show") :whole? false}
           (get-in (show/arrived {} url (feed "A Show")) [:feeds url]))
        "a start that a pointer on a link asked for, kept by the URL of its feed")))

(deftest where-a-feed-stands
  (let [guid "g"]
    (is (nil? (show/status (on-page guid) guid)) "nothing loads it yet")
    (is (= :loading (show/status (show/requested (on-page guid) [:open url]) guid))
        "while the cache is read")
    (is (= :loading (show/status (assoc-in (on-page guid) [:feeds url] {:feed (feed "A Show") :whole? false}) guid))
        "and from the start of the feed until the whole has arrived")
    (is (= :failed (show/status (assoc-in (on-page guid) [:feeds url :error] {:message "No"}) guid)))
    (is (= :loading (show/status (-> (on-page guid)
                                     (assoc-in [:feeds url :error] {:message "No"})
                                     (show/requested [:whole url]))
                                 guid))
        "but not while it's fetched again")
    (let [done (show/whole (on-page guid) url {:feed (feed "A Show")} #inst "2026-10-18T12:00:00Z" false)]
      (is (= :done (show/status done guid)))
      (is (= :checking (show/status (show/requested done [:check url]) guid))))
    (is (= :looking-up (show/status (show/requested {} [:look-up guid]) guid)))))

(deftest where-the-feed-of-a-show-is
  (let [guid  "917393e3-1b1e-5cef-ace4-edaa54e1f810"
        found {:feed-urls {guid {:url url :from :index}}}]
    (is (= {:url url} (show/source found guid)) "where the Podcast Index found it")
    (is (= url (:url (show/source (on-page guid) guid))) "or where the link to its page says")
    (is (nil? (:url (show/source (on-page "another") guid)))
        "but not the link to the page of another show")
    (is (nil? (show/source {:feed-urls {guid {:not-found? true}}} guid))
        "and nowhere when the index doesn't know it")))

(deftest a-feed-gone-from-the-url-in-the-cache
  (let [guid   "g"
        cached {:feed-urls {guid {:url url :from :cache}}}]
    (is (show/gone-from-cache? cached guid {:status 404}))
    (is (show/gone-from-cache? cached guid {:status 410}))
    (is (not (show/gone-from-cache? cached guid {:status 500})) "rather than a server that fails")
    (is (not (show/gone-from-cache? {:feed-urls {guid {:url url :from :index}}} guid {:status 404}))
        "nor a URL that the index gave just now")
    (is (not (show/gone-from-cache? (on-page guid) guid {:status 404}))
        "nor one that the link gave")))

(deftest the-artwork-of-a-search-in-its-sizes
  (let [guid  "g"
        sizes [{:href "https://i/100.jpg" :width 100} {:href "https://i/600.jpg" :width 600}]
        state (on-page {:searches {"q" {:results [{:guid guid :url url :title "A Show"
                                                   :artwork "https://i/600.jpg" :artwork-sizes sizes}]}}}
                       guid)
        fed   (-> (feed "A Show" "One")
                  (assoc :itunes/image {:href "https://i/3000.jpg"}))
        page  (show/page (show/arrived state url fed) guid)]
    (is (= "https://i/600.jpg" (:artwork page)) "rather than the feed's one size")
    (is (= sizes (:artwork-sizes page)))))

(deftest the-best-source-of-a-page
  (let [guid  "917393e3-1b1e-5cef-ace4-edaa54e1f810"
        found (on-page {:searches {"pc20" {:results [{:guid guid :title "From the search" :url url}]}}} guid)]
    (is (= "From the search" (:title (show/page found guid))) "what a search knew, at once")
    (is (= [] (:episodes (show/page found guid))))
    (is (= "From the search"
           (:title (show/page (show/requested found [:whole url]) guid)))
        "and while the feed is on its way")
    (is (= "From the feed"
           (:title (show/page (show/arrived found url (feed "From the feed")) guid)))
        "the feed once it has arrived")
    (is (nil? (show/page {} guid)) "a show that nothing knows")))

(deftest a-show-that-the-library-keeps
  (let [lib (library/subscribe (library/make "test") url (feed "Kept" "One"))
        k   (key (first (library/subscriptions lib)))]
    (is (= k (show/library-key lib k nil)) "by its key")
    (is (= k (show/library-key lib "another-guid" url)) "or by the URL of its feed")
    (is (nil? (show/library-key lib "another-guid" "https://example.org/other.xml")))
    (is (= "Kept" (:title (show/page {:library lib} k))))
    (is (= [url k true] ((juxt :url :key :whole?) (show/source {:library lib} k))))
    (is (= :done (show/status {:library lib} k)))))

(deftest a-feed-from-the-cache
  (let [at      #inst "2026-10-18T12:00:00Z"
        fetched {:url url :feed (feed "A Show" "One") :etag "\"v1\"" :status 200}]
    (is (= {:url url :feed (feed "A Show" "One") :etag "\"v1\""} (show/kept fetched))
        "the feed and the validators of its fetch")
    (is (show/fresh? fetched at #inst "2026-10-18T12:59:59Z")
        "for an hour when its server names no time")
    (is (not (show/fresh? fetched at #inst "2026-10-18T13:00:01Z")))
    (is (not (show/fresh? (assoc fetched :not-before #inst "2026-10-18T12:01:00Z")
                          at #inst "2026-10-18T12:02:00Z"))
        "until the time that its server names")
    (let [state (show/whole (on-page "g") url fetched at false)]
      (is (= {:whole? true :validators {:etag "\"v1\""} :fetched-at at :checked? false}
             (select-keys (get-in state [:feeds url]) [:whole? :validators :fetched-at :checked?])))
      (is (= ["One"] (map :title (:episodes (show/page state "g"))))))))

(deftest a-feed-fetched-again
  (let [at     #inst "2026-10-18T12:00:00Z"
        state  (show/whole (on-page "g") url {:feed (feed "A Show" "One")} at false)
        newer  {:feed (feed "A Show" "Two" "One") :etag "\"v2\""}
        later  #inst "2026-10-18T14:00:00Z"
        titles #(map :title (:episodes (show/page % "g")))]
    (is (= 1 (show/new-episodes (feed "A Show" "One") (feed "A Show" "Two" "One"))))
    (let [waiting (show/refetched state "g" url newer later)]
      (is (= ["One"] (titles waiting)) "the list on screen stays as it is")
      (is (= 1 (get-in waiting [:feeds url :newer :count])))
      (is (= newer (show/whole-feed waiting url)) "though a subscription takes the newer feed")
      (let [shown (show/newer-shown waiting "g")]
        (is (= ["Two" "One"] (titles shown)) "until the listener asks for the new episodes")
        (is (nil? (get-in shown [:feeds url :newer])))
        (is (= {:etag "\"v2\""} (get-in shown [:feeds url :validators])))))
    (is (= ["Two" "One"]
           (titles (show/refetched (assoc-in state [:route :name] :route/episode) "g" url newer later)))
        "or at once where the page doesn't list them, e.g. an episode's")
    (is (= ["One, edited"]
           (titles (show/refetched state "g" url
                                   {:feed (assoc-in (feed "A Show" "One")
                                                    [:rss/items 0 :rss/title] "One, edited")}
                                   later)))
        "and at once when the feed brings no new episode, e.g. an edit")))

(deftest a-show-that-the-library-keeps-for-one-episode
  (let [other "https://example.org/other.xml"
        lib   (library/add-episode (library/make "test") other (feed "Played" "One" "Two") "One")
        k     (first (keys (:feeds lib)))]
    (is (some? (get-in lib [:feeds k :feed])) "the library keeps its feed")
    (is (nil? (show/library-key lib k other))
        "but its page is that of a show outside it, since the feed has only that episode")))

(deftest more-episodes
  (is (= [[:store/assoc-in [:episodes-shown "g"] 40]
          [:store/assoc :announcement "Showing 40 of 45 episodes"]]
         (show/more-shown {} "g" 45)))
  (is (= [[:store/assoc-in [:episodes-shown "g"] 45]
          [:store/assoc :announcement "Showing 45 of 45 episodes"]]
         (show/more-shown {:episodes-shown {"g" 40}} "g" 45))))
