(ns dk.simongray.elektrolyt.ui.controls
  "The controls that several views share: icons, buttons with icons,
  folded sections and selects.

  The icon and its buttons are Replicant aliases, e.g.

      [controls/icon-button {:icon icons/pause :on {...}} \"Pause\"]"
  (:require [clojure.string :as str]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [replicant.alias :refer [defalias]]))

;; The icon is hidden from a screen reader, since the control that holds it
;; has a name of its own. In forced colours, browsers keep an svg's
;; currentColor in step with the text, where a masked background would
;; take the background's colour and vanish. The tooltip is SVG's own
;; title, which a screen reader skips with the icon. Chrome would read a
;; title attribute on the control again after its name when the icon holds
;; text, e.g. a skip's seconds.
;; https://www.sarasoueidan.com/blog/accessible-icon-buttons/
;; https://www.w3.org/TR/css-color-adjust-1/#forced-color-adjust-prop
;; https://www.w3.org/TR/SVG2/struct.html#TitleElement
(defalias icon
  "An svg of the path that `attrs` has as :icon, in the colour of the
  text, with its :title, if any, as a tooltip and the `children` drawn
  over the path, e.g. the seconds of a skip."
  [attrs children]
  (into [:svg.icon (merge {:viewBox     "0 0 256 256"
                           :width       "1em"
                           :height      "1em"
                           :fill        "currentColor"
                           :aria-hidden "true"}
                          (dissoc attrs :icon :title))
         (when-let [title (:title attrs)]
           [:title title])
         [:path {:d (:icon attrs)}]]
        children))

(defn icon-of
  "The icon of `x`, a path or an icon already drawn, with the `title`, if
  any, as its tooltip."
  ([x]
   (icon-of x nil))
  ([x title]
   (cond-> (if (string? x) [icon {:icon x}] x)
     title (assoc-in [1 :title] title))))

(def drawn-to
  "Where the drawing of each icon that sits among words ends, out of the
  256 units of its box, so that it sits on their baseline."
  {icons/arrow-square-out 222
   icons/envelope-simple  206
   icons/puzzle-piece     222
   icons/rss              206})

(defn in-text
  "The icon of the `path` for use among words, on their baseline, with the
  `title`, if any, as its tooltip."
  ([path]
   (in-text path nil))
  ([path title]
   [icon (cond-> {:icon path :class "in-text"}
           title           (assoc :title title)
           (drawn-to path) (assoc :style {:--drawn-to (str (drawn-to path))}))]))

(def path-numbers
  "How many numbers each command of an SVG path takes."
  {"M" 2 "L" 2 "T" 2 "H" 1 "V" 1 "C" 6 "S" 4 "Q" 4 "A" 7 "Z" 0})

(defn- coordinate
  "What the `i`th number of a group of the path command `c` is: :x, :y,
  a :length, or nil for one that's the same at any size, e.g. an arc's
  flag."
  [c i]
  (case (str/upper-case c)
    ("M" "L" "T" "C" "S" "Q") (if (even? i) :x :y)
    "H"                       :x
    "V"                       :y
    "A"                       (case i (0 1) :length 5 :x 6 :y nil)))

(defn- number-text
  "The number `n` to two decimals, as an icon's path writes it."
  [n]
  (let [rounded (/ (Math/round (* n 100.0)) 100.0)]
    (if (== rounded (Math/floor rounded)) (str (long rounded)) (str rounded))))

;; SVG 2, path data: an uppercase command's coordinates are absolute and a
;; lowercase one's relative to the point before, so that only the absolute
;; ones move, and an arc's radii grow with the drawing but not its
;; rotation or flags
;; https://www.w3.org/TR/SVG2/paths.html#PathData
(defn placed
  "The icon `path` drawn `scale` times as big, with the corner of its box
  at `x` and `y`, e.g. to draw one icon inside another."
  [path scale x y]
  (letfn [(moved [c i n]
            (let [absolute? (= c (str/upper-case c))]
              (case (coordinate c i)
                :x      (cond-> (* n scale) absolute? (+ x))
                :y      (cond-> (* n scale) absolute? (+ y))
                :length (* n scale)
                n)))]
    (->> (re-seq #"[A-Za-z]|-?(?:\d*\.\d+|\d+)" path)
         (reduce (fn [[out c i] token]
                   (if-let [n (parse-double token)]
                     [(conj out (number-text (moved c (mod i (path-numbers (str/upper-case c))) n))) c (inc i)]
                     [(conj out token) token 0]))
                 [[] nil 0])
         first
         (str/join " "))))

(defalias button
  "A button with the icon that `attrs` has as :icon and then the
  `children`, its words, with the rest of `attrs`."
  [attrs children]
  (into [:button.with-icon (merge {:type "button"} (dissoc attrs :icon))
         (icon-of (:icon attrs))]
        children))

;; The words are visually hidden text rather than an aria-label, which
;; browsers' translations still skip. NN/g finds that few icons are
;; understood without words, so this button is for those few, e.g. Play.
;; https://adrianroselli.com/2019/11/aria-label-does-not-translate.html
;; https://www.nngroup.com/articles/icon-usability/
(defalias icon-button
  "A button that shows only the icon that `attrs` has as :icon, with the
  rest of `attrs`, named by the `children`, its words, which are for a
  screen reader and the tooltip."
  [attrs children]
  [:button.icon-button (merge {:type "button"} (dissoc attrs :icon))
   (icon-of (:icon attrs) (apply str children))
   [:span.visually-hidden children]])

;; GOV.UK's buttons can ignore the second click of a double click, so that
;; a form isn't sent twice. Here, it would undo the first on a button that
;; turns into its opposite, e.g. Subscribe into Unsubscribe. A click's
;; detail counts the clicks in a row, and is 0 for the keyboard.
;; https://design-system.service.gov.uk/components/button/#stop-users-from-accidentally-sending-information-more-than-once
;; https://developer.mozilla.org/en-US/docs/Web/API/UIEvent/detail
(defn single-click
  "A click handler of the `actions` that ignores the second click of a
  double click and any after it."
  [actions]
  [[:event/single-click [:event/click-count] actions]])

(defn unless-repeated
  "The `actions` of a click, or nil when its `click-count` shows that it
  follows another, whatever the `_state`."
  [_state click-count actions]
  (when (<= (or click-count 0) 1)
    actions))

;; WAI-ARIA APG, disclosure: a button in the heading, with the heading's
;; words, that says whether its content is expanded. The heading can take
;; the focus, for a link to the section.
;; https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/
(defn disclosure
  "A heading `tag` with the `id`, whose `text` is a button that dispatches
  the `actions` to show the element `region`, or to hide it when `open`."
  [tag id text region open actions]
  [tag {:id id :tabindex -1}
   [:button.disclosure {:type          "button"
                        :aria-expanded (str (boolean open))
                        :aria-controls region
                        :on            {:click actions}}
    text]])

(defn open?
  "Whether the listener opened the folded section at `place` in `state`."
  [state place]
  (boolean (get-in state [:open place])))

(defn folded
  "A folded section in `state` with the `opts` below, whose heading is a
  button that shows or hides the content from `content-fn`. It's closed
  until the listener opens it, so that the page leads with its own
  content, and the content is only made then.

  - :id, the id of the heading, which names the section
  - :heading, its words
  - :place, where the state keeps that it's open, e.g. [:chapters id]
  - :class, the class of the section, or nil"
  [state {:keys [id heading place class] :as opts} content-fn]
  (let [open   (open? state place)
        region (str id "-content")]
    [:section (cond-> {:aria-labelledby id} class (assoc :class class))
     (disclosure :h2 id heading region open
                 [[:store/assoc-in [:open place] (not open)]])
     [:div {:id region}
      (when open
        (content-fn))]]))

(defn option-value
  "The value of an option of a select for the value `v`, a number or a
  keyword."
  [v]
  (if (keyword? v) (name v) (str v)))

;; Adrian Roselli, Be Careful Using 'menu': a select is the control for
;; choosing a value, where a menu is for the commands of an application
;; https://adrianroselli.com/2023/05/be-careful-using-menu.html
(defn select
  "A select with the `id` of the `options`, pairs of a value and its text,
  with `chosen` selected and the `attrs` merged in. A change dispatches
  the `action`, which holds a placeholder for the new value."
  ([id options chosen action]
   (select id options chosen action nil))
  ([id options chosen action attrs]
   [:select (merge {:id id :on {:change [action]}} attrs)
    (for [[v text] options]
      [:option {:value    (option-value v)
                :selected (= (option-value v) (option-value chosen))}
       text])]))
