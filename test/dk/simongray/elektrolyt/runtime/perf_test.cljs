(ns dk.simongray.elektrolyt.runtime.perf-test
  (:require [clojure.test :refer [async deftest is]]
            [dk.simongray.elektrolyt.runtime.perf :as perf]))

(deftest the-keys-that-changed
  (let [route {:name :route/home}
        old   {:route route :library {:a 1} :player {:id 1}}]
    (is (= [] (perf/changed-keys old old)))
    (is (= [:library :picks]
           (perf/changed-keys old (-> old
                                      (assoc :library {:a 1})
                                      (assoc :picks []))))
        "by identity, so an equal value that's new counts, and a new key")
    (is (= [:player] (perf/changed-keys old (dissoc old :player))) "and a key that's gone")))

(deftest a-measure-in-the-dev-build
  (is perf/on? "since the tests run in a dev build")
  (is (= 3 (perf/measured! "test/sum" {:n 2} #(+ 1 2))))
  (is (empty? (js/performance.getEntriesByName "test/sum"))
      "which is cleared once it's made, so that repeated ones don't pile up"))

(deftest a-measure-of-a-promise
  (async done
    (let [failed (perf/measured! "test/failed" {} #(js/Promise.reject (js/Error. "no")))]
      (-> (perf/measured! "test/promise" {} #(js/Promise.resolve :done))
          (.then #(is (= :done %) "resolves as the promise does"))
          (.then (fn [_] failed))
          (.then #(is false "the failed promise resolved")
                 #(is (= "no" (ex-message %)) "and fails as it does"))
          (.finally done)))))
