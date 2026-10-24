(ns dk.simongray.elektrolyt.search-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.search :as search]))

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

(deftest what-a-directory-answered
  (is (= {:status  :done
          :results [{:url           "https://feeds.podcastindex.org/pc20.xml"
                     ;; derived from the URL, since the directory doesn't
                     ;; know the guid that the feed declares
                     :guid          "d5a1ae35-5e8b-599a-943b-95609562016d"
                     :title         "Podcasting 2.0"
                     :author        "Podcast Index LLC"
                     :artwork       nil
                     :artwork-sizes []}]}
         (search/shows->list {:results [pc20 {:rss/title "No feed"}]}))
      "a show without a feed can't be subscribed to, so it's left out")
  (is (= {:status :failed :results [] :reason "fyyd answered 500"}
         (search/shows->list {:results [] :report [{:source :fyyd :reason "fyyd answered 500"}]}))))

(deftest a-search-of-the-directories
  (let [started (search/searching [:apple :fyyd])]
    (is (= {:sources [:apple :fyyd]
            :lists   {:apple {:status :searching} :fyyd {:status :searching}}}
           started))
    (is (search/searching? started))
    (let [done (-> started
                   (assoc-in [:lists :apple] {:status :done :results [{:title "A"}]})
                   (assoc-in [:lists :fyyd] {:status :done :results [{:title "B"}]}))]
      (is (not (search/searching? done)))
      (is (= ["A" "B"] (map :title (search/results-of done))) "every result, in the order of the lists")))
  (is (= [{:title "C"}] (search/results-of {:status :done :results [{:title "C"}]})) "or of a link"))

(deftest a-list-from-the-cache
  (let [kept {:value {:status :done :results [{:title "A"}]}
              :at  #inst "2026-10-18T12:00:00Z"}]
    (is (= [:apple "podcasting 2.0"] (search/cache-key :apple "  Podcasting   2.0 "))
        "the same words, whatever their case and spacing")
    (is (search/fresh? kept #inst "2026-10-19T11:59:59Z") "for a day")
    (is (not (search/fresh? kept #inst "2026-10-19T12:00:01Z")))
    (is (not (search/fresh? nil #inst "2026-10-19T12:00:00Z")))
    (is (= {:status  :failed
            :results [{:title "A"}]
            :reason  "Apple answered 503"
            :kept-at #inst "2026-10-18T12:00:00Z"}
           (search/stale-list (search/failed "Apple answered 503") kept))
        "a directory that fails shows the list that the cache kept, with its date")
    (is (= (search/failed "x")
           (search/stale-list (search/failed "x")
                              (assoc-in kept [:value :results] []))
           (search/stale-list (search/failed "x") nil))
        "but not an empty one, nor none")))

(deftest a-search-that-failed
  (is (not (search/failed? nil)))
  (is (not (search/failed? (search/searching [:apple]))))
  (is (search/failed? {:lists {:apple {:status :failed} :fyyd {:status :done}}})
      "in one of its directories")
  (is (search/failed? {:status :done :results [] :error "No such site"})
      "or a search of a link"))

(deftest the-directories-of-a-search
  (is (= [:podcastindex :apple :fyyd :drpodcast] (search/sources {:server {:podcastindex? true}}))
      "the defaults, before the library is restored")
  (is (= [:apple :fyyd :drpodcast] (search/sources {})) "without the Podcast Index, where the server doesn't sign for it")
  (let [lib (library/update-profile (library/make "test") profile/prefer {:search-sources [:fyyd :gpodder]})]
    (is (= [:fyyd :gpodder :drpodcast] (search/sources {:library lib})) "the listener's own, in their order")
    (is (= [:fyyd :gpodder]
           (search/sources {:library (library/update-profile lib profile/prefer {::app/alternative-feeds? false})}))
        "and drpodcast.nu only while DR's feeds take its episodes"))
  (is (= "drpodcast.nu" (search/source-name :drpodcast)))
  (is (= "fyyd" (search/source-name :fyyd))))

(deftest the-podcasts-at-a-link
  (is (= ["https://example.org/podcast.xml"]
         (->> (search/feeds->search {:feeds [{:url "https://example.org/podcast.xml" :podcast? true}
                                             {:url "https://example.org/blog.xml" :podcast? false}]})
              :results
              (map :url))))
  (is (search/failed? (search/feeds->search {:feeds [] :error {:id :fetch/unreachable}}))
      "a link that couldn't be fetched, e.g. offline, fails the search")
  (is (search/failed? (search/feeds->search {:feeds [] :error {:id :fetch/status :status 503}})))
  (is (not (search/failed? (search/feeds->search {:feeds [] :error {:id :discovery/not-found}})))
      "but a link without feeds is an answer"))

(deftest the-subscribed-feeds
  (let [lib (library/subscribe (library/make "test") "https://feeds.podcastindex.org/pc20.xml")]
    (is (nil? (search/subscribed-urls nil)) "before the library is restored")
    (is (search/subscribed? (search/subscribed-urls lib) "https://feeds.podcastindex.org/pc20.xml"))
    (is (not (search/subscribed? (search/subscribed-urls lib) "https://example.org/other.xml")))))

(deftest a-submitted-search
  (is (= [[:route/push :route/search {} {:q "pc20"}]]
         (search/submitted {} "  pc20 "))
      "the navigation searches, from the cache or the directories")
  (is (= [[:route/push :route/search]]
         (search/submitted {} " ")))
  (let [listed (fn [status]
                 {:searches {"pc20" {:sources (search/sources {})
                                     :lists   {:apple {:status status :results []}}}}})]
    (is (= [[:route/push :route/search {} {:q "pc20"}]]
           (search/submitted (listed :done) "pc20"))
        "a search that went well stays")
    (is (= [[:search/run "pc20"] [:route/push :route/search {} {:q "pc20"}]]
           (search/submitted (listed :failed) "pc20"))
        "one that failed somewhere runs again, before the navigation")
    (is (= [[:search/run "pc20"] [:route/push :route/search {} {:q "pc20"}]]
           (search/submitted (assoc-in (listed :done) [:searches "pc20" :sources] [:fyyd]) "pc20"))
        "as does one that asked other directories than a search asks now")))

(deftest a-search-of-other-directories
  (let [state {:searches {"pc20" (search/searching [:apple :fyyd :drpodcast])
                          "atp.fm" {:status :done :results []}}}]
    (is (not (search/outdated? state "pc20")) "the directories that a search asks now")
    (is (search/outdated? (assoc-in state [:searches "pc20" :sources] [:apple]) "pc20")
        "rather than those before a change in Settings")
    (is (search/outdated? (assoc-in state [:searches "pc20" :sources] [:fyyd :apple :drpodcast]) "pc20")
        "or in another order")
    (is (not (search/outdated? state "atp.fm")) "a search of a link asks no directory")
    (is (not (search/outdated? state "other")) "nor one that never ran")))
