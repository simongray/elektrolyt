(ns dk.simongray.elektrolyt.ui.menu-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.menu :as menu]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]))

(defn of
  "The menu \"m\" in `state`, of two actions on Item 1."
  [state]
  (menu/menu state {:id     "m"
                    :attrs  {:class "here"}
                    :button {:id "m-button" :title "Edit" :on {:pointerdown [[:item/grab]]}}
                    :label  "Edit"
                    :items  [{:label "Copy" :icon "M0" :name "Item 1" :actions [[:item/copy 1]]}
                             {:label "Delete" :actions [[:item/delete 1]]}]}))

(def opened
  "The state with the menu \"m\" open."
  {:open {(menu/place "m") true}})

(deftest the-button
  (let [[_ attrs :as button] (first (elements :button (of {})))]
    (is (= ["false" "m"] ((juxt :aria-expanded :aria-controls) attrs)) "discloses the list")
    (is (= "Edit" (text-of button)) "named by its label")
    (is (= {:pointerdown [[:item/grab]]
            :click       [[:store/assoc-in [:open [:menu "m"]] true]
                          [:page/place-menu "m"]
                          [:page/dismiss-outside ["m-button" "m"] [[:store/assoc-in [:open [:menu "m"]] false]]]]}
           (:on attrs))
        "which a press opens, below or above it, wherever it has room, until a press outside it, with the button's other handlers")
    (is (= [[:store/assoc-in [:open [:menu "m"]] false]]
           (get-in (second (first (elements :button (of opened)))) [:on :click]))
        "and closes")))

(deftest the-list
  (let [x    (of opened)
        list (first (elements :ul.menu-list x))]
    (is (= [{:id "m" :hidden false} {:id "m" :hidden true}]
           [(second list) (second (first (elements :ul.menu-list (of {}))))])
        "hidden until it's opened")
    (is (= ["Copy Item 1" "Delete"] (map text-of (elements controls/button list)))
        "of the actions, each named by its label and what it acts on")
    (is (= ["M0" nil] (map (comp :icon second) (elements controls/button list))) "with their icons")
    (is (= {:click (into [[:item/copy 1]] (menu/closed "m" "m-button"))}
           (:on (second (first (elements controls/button list)))))
        "each of which acts and closes the menu, with the focus on its button")))

(deftest closing
  (is (= {:class "here"} (second (of {}))) "the menu's attributes while it's closed")
  (is (= {:class   "here"
          :on      {:focusout [[:event/when [:event/focus-left?] [[:store/assoc-in [:open [:menu "m"]] false]]]]
                    :keydown  [[:event/when [:event/key? "Escape"]
                                (into [[:event/prevent-default]] (menu/closed "m" "m-button"))]]}}
         (second (of opened)))
      "and while it's open, when it closes as the focus leaves it and at Escape, which a menu around it then leaves alone"))
