(ns dk.simongray.elektrolyt.search.directories-test
  (:require [clojure.test :refer [async deftest is]]
            [dk.simongray.elektrolyt.runtime.cache :as app-cache]
            [dk.simongray.elektrolyt.search.directories :as directories]))

(deftest the-checks-of-drs-series
  (async done
    (let [kept     (atom {})
          asked    (atom [])
          answers  {"gone" 404 "live" 200 "down" nil}
          send     app-cache/send
          fetch!   app-cache/fetch!
          put!     app-cache/put!
          series   #(hash-map :slug % :title %)
          checked! (fn [slug] (directories/unpublished! (series slug)))]
      ;; a stand-in for DR, which fails for "down", and for the cache
      (set! app-cache/send (fn [{:keys [url]}]
                             (let [slug (some #(when (.includes url %) %) (keys answers))]
                               (swap! asked conj slug)
                               (if-let [status (answers slug)]
                                 (js/Promise.resolve {:status status})
                                 (js/Promise.reject (js/Error. "offline"))))))
      (set! app-cache/fetch! (fn [_ k] (js/Promise.resolve (some->> (get @kept k) (hash-map :value)))))
      (set! app-cache/put! (fn [_ k v] (swap! kept assoc k v)))
      (-> (js/Promise.all #js [(checked! "gone") (checked! "live") (checked! "down")])
          (.then (fn [found]
                   (is (= [(series "gone") nil (series "down")] (vec found))
                       "a series that DR doesn't publish, or whose check fails, counts as unpublished")
                   (is (= {"gone" :unpublished "live" :published} @kept) "and DR's answers are kept")
                   (js/Promise.all #js [(checked! "gone") (checked! "live") (checked! "down")])))
          (.then (fn [found]
                   (is (= [(series "gone") nil (series "down")] (vec found)))
                   (is (= ["gone" "live" "down" "down"] @asked)
                       "so that the next search asks again only about the one that failed")))
          (.catch #(is false (str "rejected: " %)))
          (.finally #(do (set! app-cache/send send)
                         (set! app-cache/fetch! fetch!)
                         (set! app-cache/put! put!)
                         (done)))))))
