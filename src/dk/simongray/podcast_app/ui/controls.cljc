(ns dk.simongray.podcast-app.ui.controls
  "The form controls that several views share, e.g. the select of a
  choice and the button of a folded section, whose views give the
  actions, labels and layout.

  The state keeps the folded sections that the listener opened as :open,
  a map of the place of each, e.g. [:chapters id], to true.")

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
  - :class, the class of the section, or nil
  - :on-open, the actions that opening it dispatches too, e.g. reading a
    transcript"
  [state {:keys [id heading place class on-open] :as opts} content-fn]
  (let [open   (open? state place)
        region (str id "-content")]
    [:section (cond-> {:aria-labelledby id} class (assoc :class class))
     (disclosure :h2 id heading region open
                 (cond-> [[:store/assoc-in [:open place] (not open)]]
                   (not open) (into on-open)))
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
  holds the placeholder of the new value."
  [id options chosen action]
  [:select {:id id :on {:change [action]}}
   (for [[v text] options]
     [:option {:value    (option-value v)
               :selected (= (option-value v) (option-value chosen))}
      text])])
