(ns dk.simongray.podcast-app.ui.picks
  "The views of the picks, as dk.simongray.podcast-app.picks finds them: a
  section of a few episodes, a line of the words that found them, and Add
  to queue on each.

  An episode that the listener adds leaves the list, since the picks are
  never queued, so the focus moves to the same button of the next row, or
  of the row before the last, or else to the section's heading. The status
  line says what was added."
  (:require [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.text :as text]
            [dk.simongray.podcast-app.ui.lists :as lists-ui]
            [dk.simongray.podcast-app.ui.rows :as rows]
            [dk.simongray.podcast-app.ui.time :as time-ui]))

(defn control-id
  "The id of the Add to queue button of the pick `id`, by which the focus
  finds it again after a change."
  [id]
  (rows/control-id "pick" id :queue))

(defn search-link
  "A link to a search for the word `term`."
  [term]
  [:a {:href (routes/href :route/search nil {:q term})} term])

;; The words are those that the picks share with what they're like, as
;; picks/shared ranks them, and each is a link to a search for it, e.g. for
;; shows about it
(defn why
  "The line that says why the picks came up, `lead` and then the first five
  of their `terms`, each a link to a search for it, e.g. \"About relays,
  zaps and Clojure\", or nil without terms."
  [lead terms]
  (when-let [terms (seq (take 5 terms))]
    (into [:p.picks-why lead " "]
          (map (fn [[kind s]] (if (= :element kind) (search-link s) s)))
          (text/listing-parts terms))))

(defn row
  "The pick `episode` in the `state`, its artwork, title, show and length,
  and Add to queue, after which the focus goes to the element `next-focus`
  names."
  [state {:keys [id title show duration] :as episode} next-focus]
  (list
   (lists-ui/row state episode :h3 [show (some-> duration time-ui/duration)])
   (rows/button "Add to queue" title
                {:id (control-id id)
                 :on {:click [[:queue/add {:id id} title false]
                              [:page/focus next-focus]]}})))

;; A list rather than a carousel, which hides most of what it holds, and
;; needs keys, focus and announcements of its own (W3C WAI, Carousels)
;; https://www.nngroup.com/articles/mobile-carousels/
;; https://www.w3.org/WAI/tutorials/carousels/
(defn section
  "The section of the `picks` in the `state`, whose heading has the `id` and
  says `heading`, with the line of why after `lead`. Without picks, it
  says `none`, or is left out when that's nil."
  [state {:keys [id heading lead none]} {:keys [episodes terms]}]
  (when (or (seq episodes) none)
    [:section.picks {:aria-labelledby id}
     [:h2 {:id id :tabindex -1} heading]
     (if (empty? episodes)
       [:p none]
       (list
        (why lead terms)
        [:ul.picks {:role "list"}
         (for [[i {pick-id :id :as episode}] (map-indexed vector episodes)]
           (let [neighbour (rows/neighbour episodes i)]
             [:li {:replicant/key (pr-str pick-id)}
              [:article.pick
               (row state episode (if neighbour (control-id (:id neighbour)) id))]]))]))]))
