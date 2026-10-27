(ns dk.simongray.elektrolyt.ui.prefetch-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.prefetch :as prefetch]))

(deftest work-ahead-of-a-click
  (let [actions [[:show/prefetch "g" "https://example.org/feed.xml"]]
        on      (prefetch/on-intent actions)]
    (is (= [[:prefetch/soon prefetch/pointer-ms actions]] (:mouseenter on)) "once a pointer rests on a link")
    (is (= [[:prefetch/soon prefetch/focus-ms actions]] (:focus on))
        "or the focus, a while longer, since a keyboard tabs past many links")
    (is (< prefetch/pointer-ms prefetch/focus-ms))
    (is (= [[:prefetch/cancel]] (:mouseleave on) (:blur on)) "unless it moves on first")
    (is (= actions (:pointerdown on)) "and at once as a pointer presses it")))
