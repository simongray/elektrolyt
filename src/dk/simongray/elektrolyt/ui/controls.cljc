(ns dk.simongray.elektrolyt.ui.controls
  "The form controls that several views share, e.g. the select of a
  choice, the button of a folded section and the buttons with icons, whose
  views give the actions, labels and layout.

  The icon and its buttons are Replicant aliases, which a view writes as
  hiccup with the path of an icon of ui.icons, e.g.

      [controls/icon-button {:icon icons/pause :on {...}} \"Pause\"]

  The state keeps the folded sections that the listener opened as :open,
  a map of the place of each, e.g. [:chapters id], to true."
  (:require [dk.simongray.elektrolyt.ui.icons :as icons]
            [replicant.alias :refer [defalias]]))

;; An icon in the colour of the text around it, which a screen reader
;; skips, since the control that holds it has a name of its own. In forced
;; colours, browsers keep an svg's currentColor in step with the text,
;; where an icon painted as a masked background would take the colour of
;; the background and vanish. Its tooltip is SVG's own title, which a
;; screen reader skips with the icon. A title attribute on the control
;; would be read after its name again in Chrome, where the icon has text
;; in it, e.g. a skip's seconds.
;; https://www.sarasoueidan.com/blog/accessible-icon-buttons/
;; https://www.w3.org/TR/css-color-adjust-1/#forced-color-adjust-prop
;; https://www.w3.org/TR/SVG2/struct.html#TitleElement
(defalias icon
  "The svg of the path that the `attrs` have as :icon, e.g. icons/play,
  with its :title, if any, as its tooltip, the rest of the `attrs`, and
  the `children` drawn over the path, e.g. the seconds in the arrow of a
  skip."
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
  "The icon of `x`, a path of ui.icons, or `x` itself, an icon that a view
  drew, e.g. a skip with the seconds in its arrow, with the `title`, if
  any, as its tooltip."
  ([x]
   (icon-of x nil))
  ([x title]
   (cond-> (if (string? x) [icon {:icon x}] x)
     title (assoc-in [1 :title] title))))

;; Letters sit on the baseline, and so does the drawing of an icon among
;; them, by where it ends in its box, which differs from icon to icon, e.g.
;; the envelope's ends above the arrow's (layout.css)
(def drawn-to
  "Where the drawing of each icon that sits among words ends, out of the
  256 units of its box."
  {icons/arrow-square-out 222
   icons/envelope-simple  206
   icons/puzzle-piece     222
   icons/rss              206})

(defn in-text
  "The icon of the `path`, a path of ui.icons, among words, with the
  `title`, if any, as its tooltip, and its drawing on their baseline."
  ([path]
   (in-text path nil))
  ([path title]
   [icon (cond-> {:icon path :class "in-text"}
           title           (assoc :title title)
           (drawn-to path) (assoc :style {:--drawn-to (str (drawn-to path))}))]))

(defalias button
  "A button with the `children`, its words, after the icon that the
  `attrs` have as :icon, as icon-of takes it, and the rest of the `attrs`,
  e.g. :on."
  [attrs children]
  (into [:button.with-icon (merge {:type "button"} (dissoc attrs :icon))
         (icon-of (:icon attrs))]
        children))

;; Its words are visually hidden text rather than an aria-label, which
;; the browsers' translations still skip. NN/g finds that few icons are
;; understood without words, so this button is for those few, e.g. Play,
;; and its icon's tooltip shows its words under the pointer.
;; https://adrianroselli.com/2019/11/aria-label-does-not-translate.html
;; https://www.nngroup.com/articles/icon-usability/
(defalias icon-button
  "A button that shows only the icon that the `attrs` have as :icon, as
  icon-of takes it, with the rest of the `attrs`, and is named by the
  `children`, its words, which only a screen reader and a tooltip show."
  [attrs children]
  [:button.icon-button (merge {:type "button"} (dissoc attrs :icon))
   (icon-of (:icon attrs) (apply str children))
   [:span.visually-hidden children]])

;; GOV.UK's buttons can ignore the second click of a double click, so that
;; it doesn't send a form twice. Here it would undo the first, on a button
;; that turns into its opposite under the pointer, e.g. Subscribe into
;; Unsubscribe. The detail of a click counts the clicks in a row, and is 0
;; for one by the keyboard.
;; https://design-system.service.gov.uk/components/button/#stop-users-from-accidentally-sending-information-more-than-once
;; https://developer.mozilla.org/en-US/docs/Web/API/UIEvent/detail
(defn single-click
  "The click handler of the `actions` that leaves out the second click of a
  double click, and any after it."
  [actions]
  [[:event/single-click [:event/click-count] actions]])

(defn unless-repeated
  "The `actions` of a click, or none for one whose `click-count` says it
  follows another, as the expansion of :event/single-click in the
  `_state`."
  [_state click-count actions]
  (when (<= (or click-count 0) 1)
    actions))

;; WAI-ARIA APG, disclosure: a button in the heading that says whether
;; what it shows is expanded, its words the heading's, with a caret after
;; them (see look.css). The heading can take the focus, as a link to the
;; section moves it there.
;; https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/
(defn disclosure
  "The heading of the `tag`, e.g. :h2, with the `id`, whose words `text`
  are a button that shows the element `region` names, or hides it when
  it's `open`, and dispatches the `actions`."
  [tag id text region open actions]
  [tag {:id id :tabindex -1}
   [:button.disclosure {:type          "button"
                        :aria-expanded (str (boolean open))
                        :aria-controls region
                        :on            {:click actions}}
    text]])

(defn open?
  "Whether the listener opened the folded section at `place` in the
  `state`, e.g. [:chapters id]."
  [state place]
  (boolean (get-in state [:open place])))

;; Closed until the listener opens it, so that the page leads with its own
;; content, and what's folded is only made then, e.g. the picks worked out
;; or the transcript read
(defn folded
  "The folded section of the `opts` in the `state`, whose heading is the
  button that shows the rest, which `content-fn` gives, once the listener
  opened it, and hides it again. The `opts` are:

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
  with `chosen` selected, whose change dispatches the `action`, which
  holds the placeholder of the new value, with the `attrs` merged in."
  ([id options chosen action]
   (select id options chosen action nil))
  ([id options chosen action attrs]
   [:select (merge {:id id :on {:change [action]}} attrs)
    (for [[v text] options]
      [:option {:value    (option-value v)
                :selected (= (option-value v) (option-value chosen))}
       text])]))
