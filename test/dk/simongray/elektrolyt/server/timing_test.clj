(ns dk.simongray.elektrolyt.server.timing-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.elektrolyt.server.timing :as timing]))

(deftest header-test
  (is (= "queue;dur=1.5, upstream;dur=1234.0"
         (timing/header (array-map "queue" 1.5 "upstream" 1234.0)))
      "with a decimal point whatever the JVM's locale"))

(deftest with-timing-test
  (testing "the metrics that timed adds while the handler answers"
    (let [h (timing/with-timing
             (fn [_]
               (timing/timed "work" #(Thread/sleep 20))
               (timing/timed "work" #(Thread/sleep 20))
               {:status 200 :headers {}}))
          [_ ms] (re-matches #"work;dur=(\d+\.\d)"
                             (get-in (h {}) [:headers "Server-Timing"]))]
      (is (<= 40 (parse-double ms) 1000) "both times in one")))
  (testing "a time from a handler that throws"
    (let [h (timing/with-timing
             (fn [_]
               (try (timing/timed "work" #(throw (ex-info "failed" {})))
                    (catch Exception _ {:status 502}))))]
      (is (re-matches #"work;dur=\d+\.\d" (get-in (h {}) [:headers "Server-Timing"])))))
  (testing "no header without metrics, and nothing outside a response"
    (is (= {:status 200} ((timing/with-timing (fn [_] {:status 200})) {})))
    (is (= 1 (timing/timed "work" (constantly 1))))))
