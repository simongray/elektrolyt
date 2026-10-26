(ns dk.simongray.elektrolyt.show.subscription-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.show.subscription :as subscription]))

(defn subscribed [state url title fetched]
  (operations/changed state (subscription/subscribed url title fetched)))

(deftest a-subscription
  (let [url   "https://feeds.podcastindex.org/pc20.xml"
        state (subscription/subscribing {:library (library/make "test")} url)]
    (is (= #{url} (:subscribing state)))
    (testing "whose feed was fetched"
      (let [done (subscribed state url "Podcasting 2.0" nil)]
        (is (empty? (:subscribing done)))
        (is (search/subscribed? (search/subscribed-urls (:library done)) url))
        (is (= "Subscribed to Podcasting 2.0" (:announcement done)))))
    (testing "whose fetch failed"
      (let [error {:id :fetch/status :message "The server answered 404."}
            done  (subscribed state url "Podcasting 2.0" {:error error})]
        (is (empty? (:subscribing done)))
        (is (= error (get-in done [:subscribe-errors url])))
        (is (not (search/subscribed? (search/subscribed-urls (:library done)) url)))
        (is (= "Couldn't subscribe to Podcasting 2.0" (:announcement done)))))))

(deftest unsubscribing-from-a-feed-that-never-came
  (let [url "https://example.org/dead.xml"
        lib (library/update-profile (library/make "test") profile/subscribe "dead" {:url url :title "Dead"})]
    (is (= [:library/unsubscribe "dead"]
           (:operation (subscription/unsubscribed {:library lib} url "Dead"))))
    (is (nil? (:operation (subscription/unsubscribed {:library lib} "https://example.org/other.xml" "Other"))))
    (is (nil? (get-in (subscription/unsubscribing {:library lib} url) [:feeds url]))
        "and with no copy of a feed that the library doesn't have")))

(deftest an-unsubscription
  (let [url   "https://feeds.podcastindex.org/pc20.xml"
        feed  {:rss/title "Podcasting 2.0" :rss/items [{:rss/title "One" :rss/guid {:content "1"}}]}
        lib   (library/subscribe (library/make "test") url feed)
        state (subscription/unsubscribing {:library lib} url)
        done  (operations/changed state (subscription/unsubscribed state url "Podcasting 2.0"))
        items #(map :rss/title (get-in % [:feeds url :feed :rss/items]))]
    (is (= #{url} (:unsubscribing state)))
    (is (= [["One"] true false] ((juxt items #(get-in % [:feeds url :whole?]) #(get-in % [:feeds url :checked?])) state))
        "with the library's copy of the feed, which the page checks again")
    (is (empty? (:unsubscribing done)))
    (is (not (search/subscribed? (search/subscribed-urls (:library done)) url)))
    (is (= ["One"] (items done)) "so the page keeps its episodes")
    (is (= "Unsubscribed from Podcasting 2.0" (:announcement done)))))
