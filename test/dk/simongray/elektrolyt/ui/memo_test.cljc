(ns dk.simongray.elektrolyt.ui.memo-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.memo :as memo]))

(deftest the-pages-gone-back-to
  (let [made (atom 0)
        page (memo/remember-recent 2 (fn [show] (swap! made inc) {:show show}) vector)
        a    (page :a)]
    (page :b)
    (is (identical? a (page :a)) "are kept")
    (is (= 2 @made))
    (page :c)
    (is (identical? a (page :a)) "as many as it keeps, the one used longest ago going first")
    (is (= 3 @made))
    (page :b)
    (is (= 4 @made) "which is made again")))
