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
