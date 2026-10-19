(ns dk.simongray.podcast-app.ui.picks-test
  (:require [clojure.test :refer [deftest is]]
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
    (is (= [:h2 {:id "episode-picks"} "More like this"] (first (elements :h2 x))))
    (is (= "About burr, espresso, beans and grind" (text-of (elements :p.picks-why x)))
        "the first five of the words that they share, as a list")
    (is (= ["/search?q=burr" "/search?q=espresso" "/search?q=beans" "/search?q=grind"]
           (map #(:href (second %)) (elements :a (elements :p.picks-why x))))
        "each a link to a search for it")
    (is (= ["Grinders compared" "Gears"] (map text-of (elements :h3 x))))
    (is (= "Grinders comparedGears" (text-of (elements :article.pick x)))
        "each its title alone, which its page has the rest of")
    (is (empty? (buttons x)) "and no buttons")))

(deftest picks-on-request
  (let [place  [:show "g"]
        asked  (atom 0)
        picks  #(do (swap! asked inc) picked)
        closed (picks-ui/folded {:library lib} section place picks)
        open   (picks-ui/folded {:library lib :picks-open {place true}} section place picks)
        button #(second (first (elements :button.disclosure (elements :h2 %))))]
    (is (= "More like this" (text-of (elements :h2 closed))) "a button of the heading's words")
    (is (= ["false" "episode-picks-list"] ((juxt :aria-expanded :aria-controls) (button closed))))
    (is (= [[:store/assoc-in [:picks-open place] true]] (get-in (button closed) [:on :click]))
        "which opens it")
    (is (empty? (elements :article.pick closed)) "closed, without picks")
    (is (= 1 @asked) "which are only worked out once it's open")
    (is (= "true" (:aria-expanded (button open))))
    (is (= [[:store/assoc-in [:picks-open place] false]] (get-in (button open) [:on :click]))
        "and closes it again")
    (is (= ["Grinders compared" "Gears"] (map text-of (elements :h3 open))))
    (is (= "Looking for episodes like it…"
           (text-of (elements :p (picks-ui/folded {:library lib :picks-open {place true}}
                                                  section place (constantly nil)))))
        "or, before the library's index is made, that they're on the way")))

(deftest the-artwork-of-a-pick
  (let [x               (picks-ui/row {:library lib} (assoc (first (:episodes picked))
                                                            :artwork "https://example.org/art.jpg"))
        [[_ art-attrs]] (elements :a.artwork-link x)
        [[_ title-attrs]] (elements :a (elements :h3 x))]
    (is (= (:href title-attrs) (:href art-attrs)) "a link to the page that the title links to")
    (is (= [-1 "true"] ((juxt :tabindex :aria-hidden) art-attrs))
        "for a pointer alone, since the title is the link of a keyboard and a screen reader")))

(deftest a-section-without-picks
  (is (= "More like thisNothing yet."
         (text-of (picks-ui/section {:library lib} section {:episodes [] :terms []})))
      "says so, where it says anything")
  (is (nil? (picks-ui/section {:library lib} (dissoc section :none) {:episodes [] :terms []}))
      "or is left out")
  (is (nil? (picks-ui/section {:library lib} (dissoc section :none) nil))
      "as it is without an index"))
