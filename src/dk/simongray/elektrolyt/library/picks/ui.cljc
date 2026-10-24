(ns dk.simongray.elektrolyt.library.picks.ui
  "The views of the picks: a section of a few episodes, each shown as its
  artwork and title, which link to its page, and a line of the words they
  share.

  A pick is as short as a link, since its page has the rest, e.g. its show
  and the buttons that queue it."
  (:require [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.text :as text]))

(defn search-link
  "A link to a search for the word `term`."
  [term]
  [:a {:href (routes/href :route/search nil {:q term})} term])

(defn why
  "The line of why the picks came up: `lead`, then the first five of their
  `terms`, e.g. \"About relays, zaps and Clojure\", or nil without terms.
  Each term links to a search for shows about it."
  [lead terms]
  (when-let [terms (seq (take 5 terms))]
    (into [:p.picks-why lead " "]
          (map (fn [[kind s]] (if (= :element kind) (search-link s) s)))
          (text/listing-parts terms))))

;; A list rather than a carousel, which hides most of what it holds, and
;; needs keys, focus and announcements of its own (W3C WAI, Carousels)
;; https://www.nngroup.com/articles/mobile-carousels/
;; https://www.w3.org/WAI/tutorials/carousels/
(defn picks-list
  "The list of the `picks` in `state` after the line of why, which starts
  with `lead`, or else the text `none`."
  [state lead none {:keys [episodes terms] :as picks}]
  (if (empty? episodes)
    [:p none]
    (list
     (why lead terms)
     [:ul.pick-list {:role "list"}
      (for [{pick-id :id :as episode} episodes]
        [:li {:replicant/key (pr-str pick-id)}
         [:article.pick (lists-ui/row state episode :h3 nil)]])])))

(defn section
  "The `section` of the `picks` in `state`, given its heading's :id and
  :heading, the :lead of its line of why, and the :none text when there
  are no picks. It's nil with neither picks nor :none."
  [state {:keys [id heading lead none] :as section} {:keys [episodes] :as picks}]
  (when (or (seq episodes) none)
    [:section.picks {:aria-labelledby id}
     [:h2 {:id id :tabindex -1} heading]
     (picks-list state lead none picks)]))

(defn folded
  "The `section` in `state` of the picks at `place`, e.g. [:show guid],
  folded until the listener opens it, and only then filled by `picks-fn`."
  [state {:keys [id heading lead none] :as section} place picks-fn]
  (controls-ui/folded state {:id id :heading heading :place (into [:picks] place) :class "picks"}
                      #(if-let [picks (picks-fn)]
                         (picks-list state lead none picks)
                         ;; before the library's index is made
                         [:p {:role "status"} "Looking for episodes like it…"])))
