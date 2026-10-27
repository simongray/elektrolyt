(ns dk.simongray.elektrolyt.ui.loading
  "The stand-ins for what's on its way, in its shape, and the status of
  the load, which a screen reader hears and the eye doesn't see.

  The grey blocks say that something loads well enough without words, so
  a load's status, e.g. Loading the show…, is visually hidden. The blocks
  show only after a second, so a quick load shows none, and they breathe
  faintly where motion is allowed (look.css).")

;; NN/g: a skeleton screen shows the shape of a page as it loads
;; https://www.nngroup.com/articles/skeleton-screens/
(defn status
  "The status line of a load, whose `text`, e.g. Loading the show…, a
  screen reader hears and the eye doesn't see, with the `attrs`, if any."
  ([text]
   (status {} text))
  ([attrs text]
   [:p.visually-hidden (merge {:role "status"} attrs) text]))

(def lines
  "Three lines in the place of a page's text, each shorter than the one
  before, hidden from a screen reader."
  [:div.skeleton {:aria-hidden "true"} [:div] [:div] [:div]])

(defn rows
  "`n` rows in the place of a list of episodes or shows, an artwork's
  square beside two lines, hidden from a screen reader."
  [n]
  [:ol.episodes.skeleton-rows {:role "list" :aria-hidden "true"}
   (for [i (range n)]
     [:li {:replicant/key i} [:div.show-episode [:div] [:div] [:div]]])])
