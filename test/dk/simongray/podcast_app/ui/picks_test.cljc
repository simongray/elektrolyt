(ns dk.simongray.podcast-app.ui.picks-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.picks-test :refer [id lib]]
            [dk.simongray.podcast-app.ui.picks :as picks-ui]
            [dk.simongray.podcast-app.ui.player-test :refer [buttons]]
            [dk.simongray.podcast-app.ui-test :refer [elements text-of]]))

(def section
  {:id "episode-picks" :heading "More like this" :lead "About" :none "Nothing yet."})

(def picked
  "Two picks, as picks/recommended gives them, with the words that found
  them."
  {:episodes [{:id (id "Brew Lab" 0) :title "Grinders compared" :show "Brew Lab" :duration 1800}
              {:id (id "Bikes" 0) :title "Gears" :show "Bikes" :duration 600}]
   :terms    ["burr" "espresso" "beans" "grind"]})

(deftest a-section-of-picks
  (let [x (picks-ui/section {:library lib} section picked)]
    (is (= [:h2 {:id "episode-picks" :tabindex -1} "More like this"] (first (elements :h2 x))))
    (is (= "About burr, espresso and beans" (text-of (elements :p.picks-why x)))
        "the first three of the words that found them, as a list")
    (is (= ["/search?q=burr" "/search?q=espresso" "/search?q=beans"]
           (map #(:href (second %)) (elements :a (elements :p.picks-why x))))
        "each a link to a search for it")
    (is (= ["Grinders compared" "Gears"] (map text-of (elements :h3 x))))
    (is (re-find #"Brew Lab.*30m" (text-of (elements :article.pick x))) "the show and the length")
    (testing "Add to queue, after which the focus goes to the row that takes its place"
      (let [[first-add last-add] (buttons x)]
        (is (= [[:queue/add {:id (id "Brew Lab" 0)} "Grinders compared" false]
                [:page/focus (picks-ui/control-id (id "Bikes" 0))]]
               (get-in first-add [1 :on :click])))
        (is (= [:page/focus (picks-ui/control-id (id "Brew Lab" 0))]
               (second (get-in last-add [1 :on :click])))
            "the row before the last")))))

(deftest a-section-without-picks
  (is (= "More like thisNothing yet."
         (text-of (picks-ui/section {:library lib} section {:episodes [] :terms []})))
      "says so, where it says anything")
  (is (nil? (picks-ui/section {:library lib} (dissoc section :none) {:episodes [] :terms []}))
      "or is left out")
  (is (nil? (picks-ui/section {:library lib} (dissoc section :none) nil))
      "as it is without an index"))
