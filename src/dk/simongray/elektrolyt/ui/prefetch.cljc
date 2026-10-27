(ns dk.simongray.elektrolyt.ui.prefetch
  "Work done ahead of a click, e.g. fetching the start of a show's feed,
  once the listener shows that they're about to open a link: a pointer
  that rests on it, the focus that rests on it a while longer, or a
  pointer that presses it.

  One prefetch waits at a time, so a pointer that passes over a list
  starts nothing, and none waits on a connection that saves data
  (runtime/prefetch-soon!).")

;; instant.page: a pointer that rests on a link for 65 ms clicks it about
;; half the time, and leaves some 300 ms to fetch what the page needs. A
;; keyboard stops briefly at each link that it tabs past, so a focus waits
;; longer.
;; https://instant.page/
(def pointer-ms
  "How long a pointer rests on a link before its prefetch starts."
  65)

(def focus-ms
  "How long the focus rests on a link before its prefetch starts."
  300)

(defn on-intent
  "The handlers of a link's events that run the `actions` ahead of a
  click: once a pointer has rested on it for pointer-ms, or the focus for
  focus-ms, unless it moves on first, and at once as a pointer presses
  it."
  [actions]
  {:mouseenter  [[:prefetch/soon pointer-ms actions]]
   :mouseleave  [[:prefetch/cancel]]
   :focus       [[:prefetch/soon focus-ms actions]]
   :blur        [[:prefetch/cancel]]
   :pointerdown actions})
