(ns dk.simongray.elektrolyt.ui.document-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.document :as document]))

(deftest a-render-of-each-change
  (let [rendered (atom [])
        render   document/render!
        state    {:route {:name :route/home}}]
    (set! document/render! #(swap! rendered conj %))
    (try
      (document/rendered! state state)
      (is (= [] @rendered) "but not of a swap that changes nothing")
      (document/rendered! state (assoc state :x 1))
      (is (= [(assoc state :x 1)] @rendered))
      (finally
        (set! document/render! render)))))
