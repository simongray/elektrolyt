(ns dk.simongray.elektrolyt.ui.menu
  "A menu button: a button that opens a list of actions below it, which
  the state keeps open, as it keeps a folded section, until a press
  outside it, Tab out of it, Escape or the choice of an action. The list
  is a panel in the colours around it (look.css), which a caller can
  change by its --menu-surface."
  (:require [dk.simongray.elektrolyt.ui.controls :as controls]))

(defn place
  "Where the state keeps whether the menu `id` is open, under :open."
  [id]
  [:menu id])

(defn kept-open
  "The action that keeps the menu `id` open, or closed when `open` is
  false."
  [id open]
  [:store/assoc-in [:open (place id)] open])

(defn closed
  "The actions that close the menu `id` and give the focus to its button
  `button-id`."
  [id button-id]
  [(kept-open id false)
   [:page/focus button-id]])

;; WAI-ARIA APG, disclosure: the button says whether the list is expanded,
;; and which list it controls, a list of buttons, as the APG's disclosure
;; navigation menu, rather than a menu role, whose arrow keys a list of
;; buttons doesn't need. Escape and an action give the focus back to the
;; button, and a disclosure around the menu, e.g. a stack's fan, then
;; leaves the Escape alone. Each action is named by its label and then
;; what it acts on, for a screen reader and a voice (WCAG 2.2, SC 2.5.3
;; Label in Name).
;; https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/examples/disclosure-navigation/
;; https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html
(defn menu
  "The menu `id` in `state` with the `opts` below: a button that opens
  and closes it, and the list of its items, hidden while it's closed.

  - :button, the attributes of the button, e.g. its :id, :title and
    :class, with any :on but a :click, which the menu sets
  - :label, what the button shows, e.g. an icon, with its words for a
    screen reader
  - :items, maps of a :label, an :icon or nil, the :name of what it acts
    on, for a screen reader after the label, and the :actions of a press
  - :attrs, the attributes of the menu around them, e.g. its class, by
    which the page places the list, or a style with its --menu-surface"
  [state {:keys [id button label items attrs]}]
  (let [button-id (:id button)
        open      (controls/open? state (place id))
        closed    (closed id button-id)]
    [:span.menu (cond-> (or attrs {})
                  open (assoc :on {:focusout [[:event/when [:event/focus-left?] [(kept-open id false)]]]
                                   :keydown  [[:event/when [:event/key? "Escape"]
                                               (into [[:event/prevent-default]] closed)]]}))
     [:button (-> (merge {:type "button" :aria-expanded (str open) :aria-controls id} button)
                  (assoc-in [:on :click] (if open
                                           [(kept-open id false)]
                                           [(kept-open id true)
                                            [:page/place-menu id]
                                            [:page/dismiss-outside [button-id id] [(kept-open id false)]]])))
      label]
     [:ul.menu-list {:id id :hidden (not open)}
      (for [{item-label :label :keys [icon name actions]} items]
        [:li {:replicant/key item-label}
         [controls/button {:icon icon
                           :on   {:click (into (vec actions) closed)}}
          item-label
          (when name
            [:span.visually-hidden " " name])]])]]))
