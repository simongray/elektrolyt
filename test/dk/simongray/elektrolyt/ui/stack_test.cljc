(ns dk.simongray.elektrolyt.ui.stack-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.stack :as stack]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]))

(defn items
  "The items titled by the numbers `ns`, with artwork but for 3."
  [& ns]
  (for [n ns]
    {:key     (str n)
     :title   (str "Item " n)
     :img     (when-not (= 3 n) [:img {:src (str n ".jpg")}])
     :actions [[:item/open n]]}))

(def opened
  "The state with the fan of the stack \"s\" open."
  {:open {(stack/place "s") true}})

(deftest keeping-it-open
  (is (= [:store/assoc-in [:open [:stack "s"]] false] (stack/kept-open "s" false))
      "in the state, under :open, as a folded section"))

(defn of
  "The stack of the `items` in `state`, of `total` in all."
  ([items]
   (of {} items (count items)))
  ([state items total]
   (stack/stack state {:id          "s"
                       :label       "Queue"
                       :description "Next: Item 1"
                       :verb        "Open"
                       :icon        "M0"
                       :items       items
                       :total       total
                       :more        ["/all" #(str % " more")]
                       :attrs       {:class "here"}})))

(deftest the-button
  (let [x                    (of {} (items 1 2 4 5) 7)
        [_ attrs :as button] (first (elements :button.stack-button x))]
    (is (= ["false" "s"] ((juxt :aria-expanded :aria-controls) attrs)) "discloses the fan")
    (is (= {:click [(stack/kept-open "s" true)
                    [:page/dismiss-outside ["s-button" "s"] [(stack/kept-open "s" false)]]]}
           (:on attrs))
        "which a press opens, until a press outside the button and the fan")
    (let [[_ attrs] (first (elements :button.stack-button (of opened (items 1) 1)))]
      (is (= ["true" [(stack/kept-open "s" false)]] [(:aria-expanded attrs) (get-in attrs [:on :click])])
          "and closes"))
    (is (= ["Queue" "Queue"] [(text-of button) (:title attrs)])
        "named by its label beside its artwork, which is decorative, and the label its tooltip too")
    (is (= ["Next: Item 1"]
           (keep #(when (= (:aria-describedby attrs) (:id (second %))) (text-of %))
                 (elements :span.visually-hidden x)))
        "and described by what comes first")
    (is (= "Queue" (last (first (elements :button.stack-button (of (items 3))))))
        "which shows in the pile's place without artwork")
    (is (= "here" (:class attrs)) "with its attributes")))

(deftest the-pile
  (is (= ["2.jpg" "1.jpg"]
         (map (comp :src second) (elements :img (stack/pile (items 1 2 3 4)))))
      "the artwork of the first three, the first last so that it's on top, without an item's that has none")
  (is (nil? (stack/pile (items 3))) "and none without artwork"))

(deftest the-fan
  (let [x   (of opened (items 1 2 3 4 5) 7)
        fan (first (elements :div.stack-fan x))]
    (is (= [{:id "s" :hidden false} {:id "s" :hidden true}]
           [(second fan) (second (first (elements :div.stack-fan (of (items 1)))))])
        "hidden until it's opened")
    (is (= ["Open Item 1" "Open Item 2" "Open Item 3" "Open Item 4" "Open Item 5"]
           (map text-of (elements :button.stack-item fan)))
        "of the items it has, each named by what a press does")
    (is (= ["5" "4" "3" "2" "1"] (map #(get-in % [1 :style :--above]) (elements :li fan)))
        "in their order from the top, each as far above the button as it is")
    (is (= {:click (conj (stack/closed "s") [:item/open 2])}
           (:on (second (second (elements :button.stack-item fan)))))
        "which closes the fan as it acts, with the focus on the button")
    (is (= [["Open Item 1" "Open Item 1"] ["Open Item 3"]]
           (map #(keep (comp :title second) (concat (elements :span.stack-title %) (elements :span.stack-artwork %)))
                (take-nth 2 (take 3 (elements :button.stack-item fan)))))
        "and says so in the tooltip of its title and artwork, if it has any")
    (is (= (repeat 4 "M0") (map (comp :icon second) (elements controls/icon fan)))
        "and with the verb's icon over each artwork")
    (let [[_ attrs & texts] (first (elements :a.stack-more fan))]
      (is (= {:href "/all"
              :on   {:click [(stack/kept-open "s" false)]}}
             attrs)
          "and then the link to them all, which closes the fan too")
      (is (= ["6 more" "5 more" "4 more" "3 more" "2 more"] (map last (apply concat texts)))
          "with a text for each number of items that the window lets the fan show"))
    (let [bare (stack/fan opened {:id "s" :verb "Open" :items (items 1 2) :total 2})]
      (is (empty? (elements :a.stack-more bare)) "or no link, without one to go to")
      (is (empty? (elements controls/icon bare)) "and no icon without one")
      (is (empty? (elements :span.menu bare)) "nor a grip without a :move"))
    (is (= ["Move Item 1" "Move Item 2"]
           (map #(text-of (first (elements :button %)))
                (elements :span.menu (stack/fan opened {:id    "s"
                                                        :verb  "Open"
                                                        :items (map #(assoc % :move [:item/move (:key %)]) (items 1 2))
                                                        :total 5}))))
        "but one beside each item with a :move, which moves it among them all")
    (is (= {:focusout [[:event/when [:event/focus-left?] [(stack/kept-open "s" false)]]]
            :keydown  [[:event/when [:event/key? "Escape"] (stack/closed "s")]]}
           (:on (second x)))
        "which closes as the focus leaves the stack, and at Escape, which gives the focus to the button")
    (is (= {} (second (of (items 1)))) "while it's open")))

(deftest no-items
  (is (nil? (of nil)) "no stack"))
