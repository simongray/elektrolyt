(ns dk.simongray.elektrolyt.ui.stack
  "A stack of items with artwork, like a stack in the Dock of a Mac: a
  button that shows their artwork piled up, which opens a fan of the first
  few above it, each its title beside its artwork, and a link to them all."
  (:require [dk.simongray.elektrolyt.ui.controls :as controls]))

(defn place
  "Where the state keeps whether the fan of the stack `id` is open, under
  :open."
  [id]
  [:stack id])

(defn button-id
  "The id of the button of the stack `id`."
  [id]
  (str id "-button"))

(defn kept-open
  "The action that keeps the fan of the stack `id` open, or closed when
  `open` is false."
  [id open]
  [:store/assoc-in [:open (place id)] open])

(defn closed
  "The actions that close the fan of the stack `id` and give the focus to
  its button."
  [id]
  [(kept-open id false)
   [:page/focus (button-id id)]])

;; Apple's stacks show a folder's items piled up in the Dock, the newest on
;; top, and fan out the first of them upwards when it's clicked, each
;; icon's name beside it, with a link to the folder at the end of the fan
;; https://support.apple.com/guide/mac-help/use-folders-in-the-dock-mchl231f08fb/mac
;; https://en.wikipedia.org/wiki/Stacks_(Mac_OS)
(defn pile
  "The artwork of the first three `items` piled up, the first on top, or
  nil when none of them has artwork."
  [items]
  (when-let [imgs (seq (for [{:keys [key img]} (take 3 items)
                             :when img]
                         (assoc-in img [1 :replicant/key] key)))]
    ;; the first last, so that it's drawn on top
    [:span.stack-pile (reverse imgs)]))

;; What a press does and the whole title, which its line can cut short, are
;; the tooltip of the title and the artwork rather than the button, whose
;; title a screen reader would read again after its name. The verb's icon
;; over the artwork shows it too, e.g. that a press plays the item rather
;; than opens its page.
(defn fan-item
  "The list item of the `item`, `n` places above the button of the stack
  that the `opts` describe: a button with its title beside its artwork,
  whose press closes the fan and dispatches the item's actions. The verb,
  e.g. Play, comes before the title for a screen reader, and its icon, if
  any, lies over the artwork."
  [{:keys [id verb icon]} n {:keys [key title img actions]}]
  (let [tooltip (str verb " " title)]
    [:li {:replicant/key key
          ;; a string, since Replicant adds px to a number
          :style         {:--above (str n)}}
     [:button.stack-item {:type "button"
                          :on   {:click (into (closed id) actions)}}
      [:span.stack-title {:title tooltip} [:span.visually-hidden verb " "] title]
      (when img
        [:span.stack-artwork {:title tooltip}
         img
         (some-> icon controls/icon-of)])]]))

;; The fan comes right after its button in the page, so that the focus goes
;; from the button into it. It lists the items from the top down, in their
;; order, as the page of them all does, so that a keyboard goes through
;; them in the order seen, and the link to them all comes last, at the foot
;; of the fan, apart from the button, so that a press meant to close the
;; fan doesn't follow it. It closes the fan too, since the player stays as
;; the page changes. The window decides how many items the fan shows, so
;; the link has a text for each, which says how many it leaves out of the
;; total, and the page shows only the one that matches.
(defn fan
  "The fan of the stack that the `opts` describe, hidden unless it's
  `open`: its items as a list, the first at the top, and then, with :more,
  the link to its href, whose text its function gives for how many of the
  :total the fan leaves out."
  [open {:keys [id items total more] :as opts}]
  [:div.stack-fan {:id id :hidden (not open)}
   [:div.stack-fan-body
    [:ol {:role "list"}
     (map-indexed #(fan-item opts (- (count items) %1) %2) items)]
    (when-let [[href more-text] more]
      [:a.stack-more {:href href
                      :on   {:click [(kept-open id false)]}}
       (for [shown (range 1 (inc (count items)))]
         [:span {:replicant/key shown} (more-text (- total shown))])])]])

;; WAI-ARIA APG, disclosure: the button says whether the fan is expanded,
;; and which it controls. Its label names it, as visually hidden text
;; beside the artwork, which is decorative, as an icon button's words do
;; (ui/controls.cljc), and it shows in the pile's place without artwork.
;; The label is the button's tooltip too, so that a voice user says what a
;; pointer user reads. The rest is its description, which takes the
;; tooltip's place there, so that a screen reader doesn't read the name
;; twice. As in the APG's disclosure menus, the fan closes as the focus
;; leaves it, and at Escape, which gives the focus back to the button. A
;; press on the button gives it the focus, which Safari doesn't, so that a
;; press elsewhere takes the focus from the stack there too.
;; https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/
;; https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/examples/disclosure-navigation/
;; https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html
;; https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/button#clicking_and_focus
(defn stack
  "The stack in `state` with the `opts` below, or nil without items: the
  button that shows the artwork of the first few items piled up, and
  opens the fan of them.

  - :id, the id of the fan
  - :label, the button's name and tooltip, e.g. Queue, which shows in the
    pile's place when none of the first items has artwork, and else only
    to a screen reader
  - :description, what a screen reader reads after the name, e.g. what
    the first item is and how many follow it
  - :verb, what a press on an item does, e.g. Play
  - :icon, the path of the verb's icon, which lies over each item's
    artwork in the fan, or nil
  - :items, the first few items, as many as the fan shows in a tall
    window, as maps of a :key, a :title, the :img of its artwork or nil,
    and the :actions of a press
  - :total, how many items there are in all
  - :more, the href of the link to them all, and the function of how many
    the fan leaves out to its text, e.g. \"4 more\", or nil for no link,
    e.g. on the page that the link would go to
  - :attrs, the attributes of the button, e.g. its class"
  [state {:keys [id label description items attrs] :as opts}]
  (when (seq items)
    (let [open      (controls/open? state (place id))
          described (str id "-description")]
      ;; attributes either way, since a nil would be a child, which would
      ;; make the button another element as the fan opens
      [:div.stack (cond-> {}
                    open (assoc :on {:focusout [[:event/when [:event/focus-left?] [(kept-open id false)]]]
                                     :keydown  [[:event/when [:event/key? "Escape"] (closed id)]]}))
       [:button.stack-button (merge {:type             "button"
                                     :id               (button-id id)
                                     :title            label
                                     :aria-describedby described
                                     :aria-expanded    (str open)
                                     :aria-controls    id
                                     :on               {:click [(kept-open id (not open))
                                                                [:page/focus (button-id id)]]}}
                                    attrs)
        (if-let [artwork (pile items)]
          (list artwork [:span.visually-hidden label])
          label)]
       [:span.visually-hidden {:id described} description]
       (fan open opts)])))
