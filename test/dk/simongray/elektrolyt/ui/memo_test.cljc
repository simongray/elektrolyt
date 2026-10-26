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

(deftest what-each-string-gives
  (let [made  (atom [])
        upper (memo/remember-each (fn [s] (swap! made conj s) (some-> s .toUpperCase)) 2)]
    (is (= "A" (upper "a")))
    (is (= "A" (upper (subs "ab" 0 1))) "is kept, for an equal string too")
    (is (nil? (upper nil)))
    (upper nil)
    (is (= ["a" nil] @made) "or a result of nil")
    (upper "b")
    (upper "a")
    (is (= ["a" nil "b" "a"] @made) "for as many strings as it keeps, the first kept going first")))
