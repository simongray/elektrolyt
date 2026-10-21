(ns dk.simongray.elektrolyt.fetching-test
  (:require [cljs.reader :as reader]
            [clojure.test :refer [async deftest is]]
            [dk.simongray.elektrolyt.fetching :as fetching]))

(defn stand-in
  "A stand-in for the browser's Worker, since Node has none, which adds each
  worker that it makes to the atom `made`, as a map of the :listeners by
  type, the messages :posted to it and whether it's :terminated."
  [made]
  (fn [_]
    (let [listeners  (atom {})
          posted     (atom [])
          terminated (atom false)]
      (swap! made conj {:listeners listeners :posted posted :terminated terminated})
      #js {:addEventListener (fn [type f] (swap! listeners update type conj f))
           :postMessage      #(swap! posted conj (reader/read-string %))
           :terminate        #(reset! terminated true)})))

(defn fire!
  "Call the listeners of `type` on the stand-in worker `w` with the event `e`."
  [w type e]
  (doseq [f (get @(:listeners w) type)]
    (f e)))

(deftest a-worker-that-failed
  (async done
    (let [made (atom [])]
      (set! (.-Worker js/globalThis) (stand-in made))
      (reset! fetching/web-worker nil)
      (let [failed   (fetching/fetch-due! [] {} nil)
            _        (fire! (first @made) "error" #js {:message "The script didn't load"})
            answered (fetching/fetch-due! [] {} nil)
            another  (second @made)]
        (is @(:terminated (first @made)) "stops")
        (is (= 2 (count @made)) "and the next ask starts another")
        (fire! another "message" #js {:data (pr-str {:id    (:id (first @(:posted another)))
                                                     :busy? true})})
        (-> failed
            (.then #(is false "the worker that failed answered")
                   #(is (= "The script didn't load" (ex-message %))
                        "which rejects what waited for the one that failed"))
            (.then (fn [_] answered))
            (.then #(is (= {:busy? true} %) "and answers what's asked of it"))
            (.catch #(is false (str "rejected: " %)))
            (.finally #(do (reset! fetching/web-worker nil)
                           (js-delete js/globalThis "Worker")
                           (done))))))))
