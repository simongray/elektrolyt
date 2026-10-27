(ns dk.simongray.elektrolyt.ui.colors
  "The colours that the page takes from artwork.

  The player takes the seed of what plays, or of its chapter's image, and
  the paper's links, buttons and tint take the seed of the show on a Show
  or Episode page. Without a seed, or when the listener prefers the app's
  own colours, both keep the app's near-black.

  The :colors of the state hold each artwork's colours by URL, as a map of
  its :seed, nil without a hue, and its :dominant colour, or of a :status
  of :loading while they're worked out."
  (:require [dk.simongray.elektrolyt.library.inbox :as inbox]
            [dk.simongray.elektrolyt.library.picks :as picks]
            [dk.simongray.elektrolyt.library.queue :as queue]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.library.shows :as shows]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]))

(defn source
  "The URL to take the colours of the artwork of `x` from: its smallest
  size, since the colours need few pixels, or else its :artwork."
  [{:keys [artwork] :as x}]
  (or (:href (first (sort-by :width (artwork/sizes x)))) artwork))

(defn entry
  "The entry in `state` for the artwork of `x`: its colours, a :status of
  :loading while they're worked out, or nil."
  [state x]
  (some->> (:artwork x) (get (:colors state))))

(defn seed
  "The seed of the artwork of `x` in `state`, or nil while it's unknown or
  when the artwork has no hue."
  [state x]
  (:seed (entry state x)))

(defn playing
  "What colours the player in `state`: the image of the current chapter,
  or else the playback that the player shows, or nil."
  [{:keys [player] :as state}]
  (let [playback (player/shown-playback state)
        chapter  (player/chapter-at playback (:current-time (player/shown-media player)))]
    (if-let [image (:img chapter)]
      {:artwork image}
      playback)))

(defn page-show
  "The show of the Show or Episode page in `state`, or nil on any other
  page."
  [state]
  (when (#{:route/show :route/episode} (get-in state [:route :name]))
    (show/page state (show/route-guid state))))

(defn focus
  "What the paper of the page in `state` takes its colours from: the show
  on a Show or Episode page once its seed is known, or else nil for the
  app's own colours."
  [state]
  (when (settings/artwork-colors? state)
    (let [page (page-show state)]
      (when (seed state page)
        page))))

(defn listed
  "The episodes or shows in the lists of Home, the Queue, the Inbox or the
  Library when `state` is on one of those pages. They're the listener's
  own, so their colours are worth working out ahead."
  [{:keys [library] :as state}]
  (when library
    (case (get-in state [:route :name])
      :route/home    (concat (:episodes (queue/view library))
                             (:episodes (:picks (picks/kept state [:home]))))
      :route/queue   (concat (:episodes (queue/view library))
                             (:episodes (:picks (picks/kept state [:queue]))))
      :route/inbox   (inbox/episodes state)
      :route/library (concat (inbox/episodes state) (shows/all-shows library))
      nil)))

(defn once-each
  "The maps `xs` with each :artwork once, the first of each, in order."
  [xs]
  (first (reduce (fn [[kept seen] x]
                   (if (seen (:artwork x))
                     [kept seen]
                     [(conj kept x) (conj seen (:artwork x))]))
                 [[] #{}]
                 xs)))

(defn wanted
  "The artworks on the page in `state` whose colours aren't in the :colors
  yet, as maps of :artwork and :artwork-sizes, each once. Those that colour
  the page come first, and what plays next is among them on every page,
  for its square in the player."
  [state]
  (->> (concat [(playing state) (first (queue/up-next state)) (page-show state)]
               (listed state))
       (filter :artwork)
       (remove #(contains? (:colors state) (:artwork %)))
       (map #(select-keys % [:artwork :artwork-sizes]))
       (once-each)))

(defn bar-seed
  "The seed for the bar of `x` in `state`: its artwork's seed, or nil while
  that's unknown or when the listener prefers the app's own colours."
  [state x]
  (when (settings/artwork-colors? state)
    (seed state x)))

(defn attrs
  "The attributes of the page's shell in `state`, with the paper's seed as
  the --seed of its style and the player's as its --bar-seed. Until a seed
  is known, its part of the page keeps the app's own colours."
  [state]
  (let [paper (some->> (focus state) (seed state))
        bars  (bar-seed state (playing state))
        style (cond-> {}
                paper (assoc :--seed paper)
                bars  (assoc :--bar-seed bars))]
    (if (seq style) {:style style} {})))

;; Timelthaler, Dominant colors for lazy-loading images: Pinterest and
;; Google Images show an image's dominant colour in its place while it
;; loads, so that the page has its colours at once
;; https://manu.ninja/dominant-colors-for-lazy-loading-images/
(defn placeholder
  "The attributes that put the dominant colour of the artwork of `x` in
  `state` behind its img while it loads, or nil while that's unknown."
  [state x]
  (when-let [c (:dominant (entry state x))]
    {:style {:background-color c}}))

(comment
  (require '[dk.simongray.elektrolyt.player-test :as player-test])
  ;; the artwork of what plays, for the player
  (wanted (assoc (player-test/state {}) :route {:name :route/queue}))
  ;; => [{:artwork "https://example.org/a.jpg"}]
  #_.)
