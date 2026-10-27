(ns dk.simongray.elektrolyt.ui.document-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.ui.document :as document]))

(deftest a-render-of-each-change
  (let [rendered (atom [])
        render   document/render!
        visible? runtime/visible?
        state    {:route {:name :route/home}}]
    (set! document/render! #(swap! rendered conj %))
    (set! runtime/visible? (constantly true))
    (try
      (document/rendered! state state)
      (is (= [] @rendered) "but not of a swap that changes nothing")
      (is (true? (document/rendered! state (assoc state :x 1))))
      (is (= [(assoc state :x 1)] @rendered))
      (finally
        (set! document/render! render)
        (set! runtime/visible? visible?)))))

(deftest no-render-of-the-clock-while-hidden
  (let [rendered (atom [])
        render   document/render!
        visible? runtime/visible?
        state    {:route  {:name :route/home}
                  :player {:id [:feed "ep"] :media {:paused false :current-time 75}}}
        ticked   (assoc-in state [:player :media :current-time] 76)]
    (set! document/render! #(swap! rendered conj %))
    (set! runtime/visible? (constantly false))
    (try
      (is (nil? (document/rendered! state ticked)))
      (is (= [] @rendered) "since nobody sees the position move")
      (document/rendered! ticked (assoc-in ticked [:player :media :paused] true))
      (is (= 1 (count @rendered)) "but a pause renders")
      (set! runtime/visible? (constantly true))
      (document/rendered! state ticked)
      (is (= 2 (count @rendered)) "as does the clock while the page is shown")
      (finally
        (set! document/render! render)
        (set! runtime/visible? visible?)))))
