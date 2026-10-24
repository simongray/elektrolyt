(ns dk.simongray.elektrolyt.library.refresh-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.library.refresh :as refresh]))

(def at
  (js/Date. 1000000))

(deftest the-wait-after-a-refresh-that-didnt-run
  (is (nil? (refresh/held-until {:running? false})) "nothing holds back the next")
  (is (= (+ 1000000 refresh/busy-wait-ms) (refresh/held-until {:busy-at at}))
      "a while after finding another tab refreshing")
  (is (= [(+ 1000000 60000) (+ 1000000 120000) (+ 1000000 refresh/max-wait-ms)]
         (map #(refresh/held-until {:failed-at at :failures %}) [1 2 10]))
      "a minute after a failure, twice as long after each in a row, and at most an hour"))

(deftest a-refresh-that-fails-again-and-again
  (let [once  (refresh/failed {} false at)
        twice (-> once (refresh/started false) (refresh/failed true at))]
    (is (= 1 (get-in once [:refresh-status :failures])))
    (is (nil? (:announcement once)) "said only when the listener asked")
    (is (= 2 (get-in twice [:refresh-status :failures])) "a start keeps the count")
    (is (= "Couldn't check for new episodes" (:announcement twice)))))

(deftest a-refresh-that-finds-another-tab-refreshing
  (let [busy (refresh/busy {} true at)]
    (is (= {:running? false :busy-at at} (:refresh-status busy)))
    (is (= "Another tab is checking for new episodes" (:announcement busy)))))
