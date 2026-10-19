(ns dk.simongray.podcast-app.ui.controls
  "The form controls that several views share, e.g. the select of a
  choice, whose views give the actions, labels and layout.")

;; WAI-ARIA APG, disclosure: a button in the heading that says whether
;; what it shows is expanded, its words the heading's, with a caret after
;; them (see look.css)
;; https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/
(defn disclosure
  "The heading of the `tag`, e.g. :h2, with the `id`, whose words `text`
  are a button that shows the element `region` names, or hides it when
  it's `open`, and dispatches the `actions`."
  [tag id text region open actions]
  [tag {:id id}
   [:button.disclosure {:type          "button"
                        :aria-expanded (str (boolean open))
                        :aria-controls region
                        :on            {:click actions}}
    text]])

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
