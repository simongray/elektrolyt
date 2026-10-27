(ns dk.simongray.elektrolyt.library.picks.ui-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.library.picks-test :refer [id lib]]
            [dk.simongray.elektrolyt.library.picks.ui :as picks-ui]
            [dk.simongray.elektrolyt.player.ui-test :refer [buttons]]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]))

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
    (is (= [:h2 {:id "episode-picks" :tabindex -1} "More like this"] (first (elements :h2 x)))
        "whose heading a link to the section moves the focus to")
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
        opened {:library lib :open {[:picks :show "g"] true}}
        closed (picks-ui/folded {:library lib} section place picks)
        open   (picks-ui/folded opened section place picks)
        button #(second (first (elements :button.disclosure (elements :h2 %))))]
    (is (= "More like this" (text-of (elements :h2 closed))) "a button of the heading's words")
    (is (= ["false" "episode-picks-content"] ((juxt :aria-expanded :aria-controls) (button closed))))
    (is (= [[:store/assoc-in [:open [:picks :show "g"]] true]] (get-in (button closed) [:on :click]))
        "which opens it")
    (is (empty? (elements :article.pick closed)) "closed, without picks")
    (is (= 1 @asked) "which are only worked out once it's open")
    (is (= "true" (:aria-expanded (button open))))
    (is (= [[:store/assoc-in [:open [:picks :show "g"]] false]] (get-in (button open) [:on :click]))
        "and closes it again")
    (is (= ["Grinders compared" "Gears"] (map text-of (elements :h3 open))))
    (is (= "Looking for episodes like it…"
           (text-of (elements :p.visually-hidden (picks-ui/folded opened section place (constantly nil)))))
        "or, before the library's index is made, that they're on the way, to a screen reader")
    (is (seq (elements :ol.episodes.skeleton-rows (picks-ui/folded opened section place (constantly nil))))
        "and a row in their place to the eye")))

(deftest the-artwork-of-a-pick
  (let [x               (picks-ui/section {:library lib} section
                                          (assoc-in picked [:episodes 0 :artwork] "https://example.org/art.jpg"))
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
