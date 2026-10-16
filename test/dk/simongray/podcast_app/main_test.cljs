(ns dk.simongray.podcast-app.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.main :as main]
            [dk.simongray.podcast-app.search :as search]))

(def inbox
  {:name :route/inbox :path-params {} :query-params {}})

(def library-page
  {:name :route/library :path-params {} :query-params {}})

(defn search-page
  "The route of the Search page for the query `q`."
  [q]
  {:name :route/search :path-params {} :query-params (if q {:q q} {})})

(deftest the-first-page-keeps-the-focus
  (is (= [[:store/assoc :route inbox]]
         (main/navigated {} inbox))))

(deftest another-page-takes-the-focus
  (is (= [[:store/assoc :route library-page] [:page/focus-heading]]
         (main/navigated {:route inbox} library-page)))
  (is (= [[:store/assoc :route inbox] [:page/focus-heading]]
         (main/navigated {:route nil} inbox))))

(deftest the-same-page-keeps-the-focus
  (is (= [[:store/assoc :route inbox]]
         (main/navigated {:route inbox} inbox)))
  (is (= [[:store/assoc :route (search-page "b")] [:search/run "b"]]
         (main/navigated {:route (search-page "a")} (search-page "b")))
      "another query of the Search page"))

(deftest a-query-in-the-url-searches-once
  (is (= [[:store/assoc :route (search-page "x")] [:search/run "x"]]
         (main/navigated {} (search-page "x")))
      "a link to a search, or a reload")
  (is (= [[:store/assoc :route (search-page "x")] [:page/focus-heading]]
         (main/navigated {:route inbox :searches {"x" {:status :done}}} (search-page "x")))
      "Back to a search that's kept")
  (is (= [[:store/assoc :route (search-page nil)]]
         (main/navigated {} (search-page nil)))))

(deftest the-page-of-a-show-loads-it
  (let [route {:name         :route/show
               :path-params  {:guid "g"}
               :query-params {:url "https://example.org/feed.xml"}}]
    (is (= [[:store/assoc :route route]
            [:page/focus-heading]
            [:show/load "g" "https://example.org/feed.xml" false]]
           (main/navigated {:route inbox} route)))))

(deftest more-episodes
  (is (= [[:store/assoc-in [:shows "g" :shown] 40]
          [:store/assoc :announcement "Showing 40 of 45 episodes"]]
         (main/more-shown {} "g" 45)))
  (is (= [[:store/assoc-in [:shows "g" :shown] 45]
          [:store/assoc :announcement "Showing 45 of 45 episodes"]]
         (main/more-shown {:shows {"g" {:shown 40}}} "g" 45))))

(deftest the-canonical-path-of-a-show
  (let [url     "https://feeds.podcastindex.org/pc20.xml"
        derived "d5a1ae35-5e8b-599a-943b-95609562016d"
        route   {:name :route/show :path-params {:guid derived} :query-params {:url url}}
        found   {:searches {"pc20" {:results [{:guid derived :url url :title "Podcasting 2.0"}]}}}]
    (is (= (str "/shows/" derived "/podcasting-2-0?url=https%3A%2F%2Ffeeds.podcastindex.org%2Fpc20.xml")
           (main/canonical-path (assoc found :route route :library (library/make "test"))))
        "a show that the library doesn't keep keeps the URL of its feed")
    (let [lib (library/subscribe (library/make "test") url {:rss/title "Podcasting 2.0"})
          k   (key (first (library/subscriptions lib)))]
      (is (= (str "/shows/" k "/podcasting-2-0")
             (main/canonical-path (assoc found :route route :library lib)))
          "and a show that it keeps has its key there and no URL"))
    (is (nil? (main/canonical-path {:route route})) "before the title is known")))

(deftest a-submitted-search
  (is (= [[:search/run "pc20"] [:route/push :route/search {} {:q "pc20"}]]
         (main/submitted {} "  pc20 ")))
  (is (= [[:route/push :route/search]]
         (main/submitted {} " "))))

(deftest a-subscription
  (let [url   "https://feeds.podcastindex.org/pc20.xml"
        state (main/subscribing {:library (library/make "test")} url)]
    (is (= #{url} (:subscribing state)))
    (testing "whose feed was fetched"
      (let [done (main/subscribed state url "Podcasting 2.0" nil)]
        (is (empty? (:subscribing done)))
        (is (search/subscribed? (search/subscribed-urls (:library done)) url))
        (is (= "Subscribed to Podcasting 2.0" (:announcement done)))))
    (testing "whose fetch failed"
      (let [error {:id :fetch/status :message "The server answered 404."}
            done  (main/subscribed state url "Podcasting 2.0" {:error error})]
        (is (empty? (:subscribing done)))
        (is (= error (get-in done [:subscribe-errors url])))
        (is (not (search/subscribed? (search/subscribed-urls (:library done)) url)))
        (is (= "Couldn't subscribe to Podcasting 2.0" (:announcement done)))))))
