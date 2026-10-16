(ns dk.simongray.podcast-app.ui.show-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.ui.show :as show]))

(deftest the-duration-of-an-episode
  (is (= "1 h 23 min" (show/duration-text 4980)))
  (is (= "2 h" (show/duration-text 7200)))
  (is (= "45 min" (show/duration-text 2700)))
  (is (= "1 min" (show/duration-text 40)) "rounded to the minute")
  (is (= "20 s" (show/duration-text 20)) "but for less than half a minute"))

(deftest the-status-of-the-episodes
  (is (= "Loading episodes…" (show/status-text :loading 20 nil)) "while the whole feed is fetched")
  (is (= "207 episodes." (show/status-text :done 207 nil)))
  (is (= "1 episode." (show/status-text nil 1 nil)) "a show that the library keeps")
  (is (= "The feed couldn't be fetched. The server answered 404."
         (show/status-text :failed 0 {:message "The server answered 404."}))))

(defn text-of
  "The text of the Hiccup `x`, as one string."
  [x]
  (apply str (filter string? (tree-seq (some-fn vector? seq?) #(if (vector? %) (rest %) %) x))))

(deftest a-show-that-is-still-on-its-way
  (let [state #(hash-map :route {:name :route/show :path-params {:guid "g"} :query-params {}}
                         :shows {"g" {:status %}})]
    (is (= "Looking for the show in the Podcast Index…" (text-of (show/content (state :looking-up)))))
    (is (= "Loading the show…" (text-of (show/content (state :loading))))
        "rather than say there's no such show")
    (is (re-find #"isn't in the library" (text-of (show/content (state :not-found)))))))

(deftest more-episodes-on-request
  (let [rows     (vec (for [i (range 45)] {:guid (str i) :title (str "Episode " i)}))
        section  (show/episodes "g" rows :done 20 nil)
        [_ & xs] section
        items    (some #(when (and (vector? %) (= :ol.episodes (first %))) (last %)) xs)
        button   (some #(when (and (vector? %) (= :button (first %))) %) xs)]
    (is (= 20 (count items)))
    (is (= [[:show/more "g" 45]] (get-in button [1 :on :click])))
    (is (nil? (some #(when (and (vector? %) (= :button (first %))) %)
                    (show/episodes "g" rows :done 45 nil)))
        "no button once they're all listed")))
