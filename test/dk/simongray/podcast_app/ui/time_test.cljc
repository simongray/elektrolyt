(ns dk.simongray.podcast-app.ui.time-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.ui.time :as time-ui]))

(deftest the-duration-of-an-episode
  (is (= "1h 23m" (time-ui/duration-text 4980)))
  (is (= "2h" (time-ui/duration-text 7200)))
  (is (= "45m" (time-ui/duration-text 2700)))
  (is (= "1m" (time-ui/duration-text 40)) "rounded to the minute")
  (is (= "20s" (time-ui/duration-text 20)) "but for less than half a minute")
  (is (= [:time {:datetime "PT4980S"} "1h 23m"] (time-ui/duration 4980))))

(deftest a-date-in-british-english
  (is (= "2 Oct 2026" (time-ui/date-text #inst "2026-10-02T12:00:00Z"))))

(deftest the-times-of-a-clock
  (is (= [:time {:datetime "PT754S"} "12:34"] (time-ui/clock 754)))
  (is (= [:time {:datetime "PT3723S"} "1:02:03"] (time-ui/clock 3723)))
  (is (= [:time {:datetime "PT5S"} "0:05"] (time-ui/clock 5.7)) "to the second below")
  (is (= [:time {:datetime "PT0S"} "–:––"] (time-ui/clock nil)) "before the metadata"))
