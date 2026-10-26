(ns dk.simongray.elektrolyt.ui.artwork.colors-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.artwork.colors :as artwork-colors]
            [dk.simongray.elektrolyt.ui.colors :as colors]))

(deftest colours-that-arrive-together-go-in-together
  (let [store   (atom {:colors {"a" {:status :loading} "b" {:status :loading}}})
        changes (atom 0)
        started (atom [])]
    (add-watch store ::changes (fn [_ _ _ _] (swap! changes inc)))
    (with-redefs [colors/wanted               (fn [state]
                                                (for [a     ["a" "b" "c" "d" "e" "f" "g"]
                                                      :when (not (contains? (:colors state) a))]
                                                  {:artwork a}))
                  artwork-colors/load-colors! (fn [_ x] (swap! started conj (:artwork x)))]
      (artwork-colors/took-arrived! store {"a" {:seed "#123456"} "b" {:seed nil}}))
    (is (= 1 @changes) "in one change of the store, which renders once")
    (is (= {:seed "#123456"} (get-in @store [:colors "a"])))
    (is (= ["c" "d" "e" "f"] @started) "with the next four started")
    (is (= {:status :loading} (get-in @store [:colors "f"]))
        "and marked as under way in the same change")))
