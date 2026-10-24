(ns dk.simongray.elektrolyt.search.drpodcast-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.elektrolyt.search.drpodcast :as drpodcast]))

(deftest dr's-own-feeds
  (is (= "genstart" (drpodcast/official-slug "https://api.dr.dk/podcasts/v1/feeds/genstart.xml")))
  (is (= "p1-morgen" (drpodcast/official-slug "https://api.dr.dk/podcasts/v1/feeds/p1-morgen.xml?format=podcast&limit=100"))
      "with the query that Apple lists them with")
  (is (= "genstart" (drpodcast/official-slug "https://api.dr.dk/podcasts/v1/feeds/genstart"))
      "or without .xml, as DR's series data names them")
  (is (nil? (drpodcast/official-slug "https://drpodcast.nu/genstart/feed.xml")))
  (is (nil? (drpodcast/official-slug "https://api.dr.dk/radio/v2/series")))
  (is (= "https://drpodcast.nu/genstart/feed.xml" (drpodcast/feed-url "genstart"))))

(deftest one-show
  (let [ours   {:rss/link "https://www.dr.dk/lyd/p1/hjernekassen-paa-p1-759510699000"}
        theirs {:rss/link "https://www.dr.dk/lyd/p1/hjernekassen-paa-p1-759510699000"}]
    (is (= "759510699000" (drpodcast/production-number ours)))
    (is (drpodcast/same-show? ours theirs) "by the production number of the series")
    (is (not (drpodcast/same-show? ours {:rss/link "https://www.dr.dk/lyd/p1/genstart-2642056922000"})))
    (is (not (drpodcast/same-show? {:rss/link "https://www.dr.dk/lyd/p1"} {:rss/link "https://www.dr.dk/lyd/p1"}))
        "and not without one")
    (is (not (drpodcast/same-show? ours nil)))))

(deftest feeds-fetched-before
  (let [dr    "https://api.dr.dk/podcasts/v1/feeds/genstart.xml?format=podcast"
        other "https://example.com/feed.xml"
        lib   {:profile {}
               :feeds   {"a" {:url dr :feed {:rss/title "Genstart"}}
                         "b" {:url dr :feed (drpodcast/marked {:rss/title "Genstart"})}
                         "c" {:url other :feed {:rss/title "Other"}}
                         "d" {:url dr}}}]
    (is (drpodcast/fetched-as? (drpodcast/marked {}) dr true) "marked while on")
    (is (not (drpodcast/fetched-as? {} dr true)) "and not before")
    (is (drpodcast/fetched-as? {} dr false))
    (is (not (drpodcast/fetched-as? (drpodcast/marked {}) dr false)) "or once turned off")
    (is (drpodcast/fetched-as? {} other true) "while any other feed is as it is")
    (is (not (drpodcast/fetched-as? {::drpodcast/supplemented 1} dr true))
        "and one that an earlier version supplemented is fetched again")
    (with-redefs [library/subscriptions (constantly {"a" {} "b" {} "c" {} "d" {}})]
      (is (= ["a"] (drpodcast/stale lib true))
          "the subscriptions to DR's own feeds that a fetch gives otherwise, with a feed held")
      (is (= ["b"] (drpodcast/stale lib false))))))

(deftest dr's-promos
  (let [item #(hash-map :rss/title %1 :itunes/duration %2)]
    (is (drpodcast/promo? (item "Hør flere afsnit af denne  i DR Lyd" 24)))
    (is (drpodcast/promo? (item " Hør hele serien i DR Lyd " 26)))
    (is (not (drpodcast/promo? (item "17.10.26 - Klar i DR Lyd" 642)))
        "but not a preview, which pairs with its whole episode")
    (is (not (drpodcast/promo? (item "Teaser" 20))))
    (is (not (drpodcast/promo? (item "Hør hele serien i DR Lyd" nil))) "nor one of unknown length")
    (is (= ["Teaser"] (map :rss/title (:rss/items (drpodcast/without-promos
                                                   {:rss/items [(item "Hør alle episoder i DR Lyd" 25)
                                                                (item "Teaser" 20)]})))))))

(def slugs
  "slugs.json as wary-fetch reads it."
  {:urn:dr:radio:series:5fa2684c330eac2f135b9254
   {:slug "hjernekassen" :productionNumber "759510699000" :title "Hjernekassen på P1"}
   :urn:dr:radio:series:5fa29618330eac2f135bbf49
   {:slug "p4-fyn-regionale-nyheder" :productionNumber "846728334000" :title "P4 Fyn regionale nyheder"}
   :urn:dr:radio:series:5fa2961833aaaaaaaaaaaaaa
   {:slug "p4-fyn-weekend" :productionNumber "1" :title "P4 Fyn Weekend"}
   :urn:dr:radio:series:broken
   {:slug "broken"}})

(deftest the-slugs
  (is (= {"759510699000" "hjernekassen" "846728334000" "p4-fyn-regionale-nyheder" "1" "p4-fyn-weekend"}
         (drpodcast/slugs slugs))
      "by production number, leaving out a series without one"))

(deftest a-search
  (is (= ["P4 Fyn regionale nyheder" "P4 Fyn Weekend"] (map :title (drpodcast/matches slugs "p4 fyn")))
      "every word, whatever the case, by title")
  (is (= ["P4 Fyn Weekend"] (map :title (drpodcast/matches slugs " Weekend  fyn "))))
  (is (empty? (drpodcast/matches slugs "genstart")))
  (let [url "https://drpodcast.nu/p4-fyn-weekend/feed.xml"]
    (is (= {:url     url
            :guid    (guid/feed-guid nil url)
            :title   "P4 Fyn Weekend"
            :author  "DR"
            :artwork "https://drpodcast.nu/p4-fyn-weekend/image.jpg"}
           (drpodcast/result {:slug "p4-fyn-weekend" :title "P4 Fyn Weekend"})))))
