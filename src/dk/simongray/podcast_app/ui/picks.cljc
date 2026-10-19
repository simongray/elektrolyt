(ns dk.simongray.podcast-app.ui.picks
  "The views of the picks, as dk.simongray.podcast-app.picks finds them: a
  section of a few episodes, each its artwork and its title, which link to
  its page, and a line of the words that found them.

  A pick is as short as a link, since its page has the rest, e.g. its show
  and the buttons that queue it."
  (:require [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.text :as text]
            [dk.simongray.podcast-app.ui.lists :as lists-ui]))

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
  "The pick `episode` in the `state`: its artwork and its title, which link
  to its page."
  [state episode]
  (lists-ui/row state episode :h3 nil))

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
     [:h2 {:id id} heading]
     (if (empty? episodes)
       [:p none]
       (list
        (why lead terms)
        [:ul.picks {:role "list"}
         (for [{pick-id :id :as episode} episodes]
           [:li {:replicant/key (pr-str pick-id)}
            [:article.pick (row state episode)]])]))]))
