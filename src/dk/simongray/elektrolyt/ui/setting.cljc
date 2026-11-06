(ns dk.simongray.elektrolyt.ui.setting
  "The parts that settings are built from, on the Settings page and in a
  show's fold: a group, its rows, and the kinds of row.

  Every row has one shape, a title and a hint followed by a control at its
  end, as in GNOME's boxed lists and Windows' settings cards, so that its
  words share one edge and its controls another. The groups are fieldsets
  with their headings in the legends, following GOV.UK, so that a screen
  reader names each control's group and lists the page's headings.
  https://developer.gnome.org/hig/patterns/containers/boxed-lists.html
  https://learn.microsoft.com/dotnet/communitytoolkit/windows/settingscontrols/settingsexpander
  https://design-system.service.gov.uk/components/fieldset/"
  (:require [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]))

;; Wikipedia's and GOV.UK's contents link to a page's headings by their
;; ids, and so do the links from other pages here. The heading takes the
;; focus like a page's main heading.
(defn group
  "A group of settings named `k`, headed `heading`, with the `children`,
  whose heading has `k` as its id for links to the group."
  [k heading & children]
  [:fieldset.settings-group
   [:legend [:h2 {:id (name k) :tabindex -1} heading]]
   children])

(defn rows
  "The `rows` of settings, stacked one under another."
  [& rows]
  [:div.setting-rows rows])

(defn hint-id
  "The id of the hint of the row `id`, for the control that it describes."
  [id]
  (str id "-hint"))

(defn row
  "The row `id` of a setting: its `title`, the `hint` under it, if any, and
  the `controls` at its end, if any."
  [id title hint & controls]
  [:div.setting
   title
   (when hint
     [:p {:id (hint-id id)} hint])
   (when (seq controls)
     [:div.setting-control controls])])

(defn choice
  "A row with a select of the `options`, labelled `label`, with the `id`,
  the `chosen` option selected and the `action` on a change."
  [id label options chosen action]
  (row id
       [:label {:for id} label]
       nil
       (controls/select id options chosen action)))

;; WAI-ARIA APG, switch pattern: a checkbox with role="switch", which every
;; browser announces as a switch and the CSS draws as one. Its label stays
;; the same as it turns on and off.
;; https://www.w3.org/WAI/ARIA/apg/patterns/switch/
;; https://webkit.org/blog/15054/an-html-switch-control/
(defn switch-input
  "A switch with the `id`, on when `on?`, with the `attrs` merged in."
  [id on? attrs]
  [:input (merge {:type    "checkbox"
                  :switch  true
                  :role    "switch"
                  :id      id
                  :checked (boolean on?)}
                 attrs)])

(defn switch
  "A row with a switch with the `id`, labelled `label` and described by the
  `hint`, on when `on?`, whose change dispatches the `action`, which holds
  a placeholder for the new value."
  [id label hint on? action]
  (row id
       [:label {:for id} label]
       hint
       (switch-input id on? {:aria-describedby (hint-id id)
                             :on               {:change [action]}})))

(defn action
  "The row `id` of something to do, with the `title`, the `hint`, and the
  `controls`, if any, e.g. a button."
  [id title hint & controls]
  (apply row id [:span.setting-title title] hint controls))

(defn fact
  "A row that only says the `value` of what's named `title`."
  [title value]
  (row nil [:span.setting-title title] nil value))

;; CSS can style a file input's button, but not the words beside it, and a
;; page can't choose the language of either. So the input stays the control
;; and takes the focus, hidden visually, and its second label looks like
;; every other button.
;; https://developer.mozilla.org/en-US/docs/Web/CSS/::file-selector-button
;; https://dev.to/jaketracey/styling-accessible-html-file-inputs-ebk
(defn file
  "A row with a file input with the `id`, labelled `title`, described by the
  `hint`, and shown as a button, with the `attrs` merged into the input."
  [id title hint attrs]
  (row id
       [:label {:for id} title]
       hint
       [:input.visually-hidden (merge {:type             "file"
                                       :id               id
                                       :aria-describedby (hint-id id)}
                                      attrs)]
       [:label.with-icon.file-button {:for id}
        (controls/icon-of icons/upload-simple)
        "Choose a file"]))
