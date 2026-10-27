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

(deftest the-keys-of-the-actions
  (is (= [:player/play :store/assoc]
         (perf/action-keys [[:player/play {:title "A secret"} 12] nil [:store/assoc :x 1]]))
      "without their arguments"))

(deftest a-dispatch-measured
  (let [{:keys [before-dispatch after-dispatch]} perf/dispatch-interceptor
        top    (before-dispatch {:actions [[:show/more "g"]]})
        nested (before-dispatch {:actions [[:store/assoc :x 1]] :trace [[:show/more "g"]]})]
    (is (number? (::perf/start top)) "one of the page's own")
    (is (nil? (::perf/start nested)) "but not one that an effect dispatches")
    (is (= top (after-dispatch top)))))

(deftest the-phases-of-an-interaction
  (is (= {:kind :interaction :type "click" :ms 160
          :input-delay 20 :processing 100 :presentation 40}
         (perf/interaction {:name "click" :start-time 1000 :duration 160
                            :processing-start 1020 :processing-end 1120}))))

(deftest the-event-of-each-interaction
  (let [event #(do #js {:name %1 :interactionId %2 :processingStart 0 :processingEnd %3})]
    (is (= ["click" "keydown"]
           (sort (map #(.-name %) (perf/slowest-events [(event "pointerdown" 1 2)
                                                        (event "click" 1 300)
                                                        (event "pointerup" 1 1)
                                                        (event "keydown" 2 40)
                                                        (event "mousemove" 0 90)]))))
        "the one whose handlers took longest, and none of events outside an interaction")))

(deftest a-long-frame-and-its-scripts
  (is (= {:kind :frame :ms 180 :blocking 130
          :scripts [{:invoker "BUTTON.onclick" :source "main.js" :ms 90 :forced-layout 12}
                    {:invoker "Promise.then" :source "shared.js" :ms 40 :forced-layout 0}]}
         (perf/frame {:duration 180 :blocking-duration 130
                      :scripts [{:invoker "Promise.then" :source "shared.js"
                                 :forced-style-and-layout 0 :duration 40}
                                {:invoker "BUTTON.onclick" :source "main.js"
                                 :forced-style-and-layout 12.4 :duration 90}]}))
      "the scripts that ran longest first"))

(deftest the-worst-kept
  (reset! perf/worst [])
  (run! #(perf/kept! {:ms %}) (range 30))
  (is (= (range 29 9 -1) (map :ms @perf/worst)) "the 20 slowest, slowest first")
  (reset! perf/worst []))

(deftest a-target-by-its-tag-and-id
  (is (= "button#play" (perf/target #js {:tagName "BUTTON" :id "play"})))
  (is (= "a" (perf/target #js {:tagName "A" :id ""})) "without its label")
  (is (nil? (perf/target nil))))

(deftest a-tally-while-hidden
  (perf/tallied! :renders)
  (is (nil? @perf/hidden-tally) "nothing while the page is shown")
  (perf/hidden!)
  (perf/tallied! :renders)
  (perf/tallied! :render-ms 2.5)
  (perf/tallied! :render-ms 4)
  (is (= {:renders 1 :render-ms 6.5} (dissoc @perf/hidden-tally :since)))
  (is (= {:minutes 2 :renders {:count 3 :per-minute 1.5} :render-ms {:count 7 :per-minute 3.3}}
         (perf/tally-summary {:since 0 :renders 3 :render-ms 6.5} 120000))
      "and once it's shown, each count with its rate a minute")
  (perf/shown!)
  (is (nil? @perf/hidden-tally) "which ends it"))

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
