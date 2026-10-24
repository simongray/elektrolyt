(ns dk.simongray.elektrolyt.library.shows-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.library.shows :as shows]))

(def all
  [{:feed-guid "a" :title "beta" :newest #inst "2026-10-01"}
   {:feed-guid "b" :title "Alpha" :newest #inst "2026-10-16"}
   {:feed-guid "c" :title "Gamma"}])

(deftest the-orders-of-the-shows
  (is (= ["b" "a" "c"] (map :feed-guid (shows/sorted all :newest)))
      "the newest episode first, and a show without dates last")
  (is (= ["b" "a" "c"] (map :feed-guid (shows/sorted all :title))) "by title, whatever the case"))

(deftest the-listeners-choices
  (is (= :newest (shows/choice {} :sort)) "the default")
  (is (= :title (shows/choice {:library-view {:sort :title}} :sort)))
  (is (= :auto (shows/choice {:library-view {:layout :sideways}} :layout)) "nor one that isn't a choice"))

(deftest what-a-show-counts
  (is (= "12 unplayed, 3 new" (shows/counts-text {:unplayed 12 :inbox 3})))
  (is (= "12 unplayed" (shows/counts-text {:unplayed 12 :inbox 0})))
  (is (nil? (shows/counts-text {:unplayed 0}))))

(deftest what-a-refresh-says
  (is (= "2 new episodes" (shows/summary {:added [1 2] :counts {:failed 0}} true)))
  (is (= "No new episodes, but 1 show couldn't be checked" (shows/summary {:added [] :counts {:failed 1}} true)))
  (is (nil? (shows/summary {:added [] :counts {}} false)) "nothing of one on its own that found nothing")
  (is (= "1 new episode" (shows/summary {:added [1] :counts {}} false))))

(deftest when-the-feeds-were-fetched
  (is (= #inst "2026-10-17" (shows/updated-at {:feeds {"a" {:fetched-at #inst "2026-10-16"}
                                                      "b" {:fetched-at #inst "2026-10-17"}
                                                      "c" {}}})))
  (is (nil? (shows/updated-at {:feeds {}}))))
