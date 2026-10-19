(ns dk.simongray.podcast-app.colors
  "The colours of artwork that the look takes, for its bars, links and
  buttons: the seed of the artwork of what the page is about, i.e. the
  show of a Show or an Episode page, or else of the episode that plays.

  The :colors of the state keep each artwork's colours by its URL, as a
  map of the :seed, a hex colour from colour-coded's image/seed, nil for
  artwork without a hue, or of the :status :loading while they're worked
  out. A page shows the app's own colours
  until they're known."
  (:require [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.show :as show]))

(defn source
  "The URL to work out the colours of the artwork of `x` from: the smallest
  of its :artwork-sizes, since the colours need few pixels, or else its
  :artwork."
  [{:keys [artwork artwork-sizes] :as x}]
  (or (:href (first (sort-by :width artwork-sizes))) artwork))

(defn seed
  "The seed of the colours of the artwork of `x` in the `state`, or nil
  while they're unknown or where it has no hue."
  [state x]
  (some->> (:artwork x) (get (:colors state)) :seed))

(defn playing
  "The playback that the player of the `state` shows, whose artwork
  colours the bars, or nil."
  [state]
  (player/shown-playback (:player state)))

(defn page-show
  "The show of the Show or Episode page of the `state`, whose artwork
  colours its links and buttons, or nil on any other page."
  [state]
  (let [{:keys [name path-params]} (:route state)]
    (when (#{:route/show :route/episode} name)
      (show/page state (:guid path-params)))))

(defn focus
  "What the page of the `state` takes its colours from: the show of a Show
  or an Episode page, once its colours are known, or else what plays."
  [state]
  (let [page (page-show state)]
    (if (seed state page)
      page
      (playing state))))

(defn wanted
  "The artworks whose colours the page of the `state` shows and that the
  :colors don't have yet, as maps of the :artwork and the :artwork-sizes."
  [state]
  (->> [(playing state) (page-show state)]
       (filter :artwork)
       (remove #(contains? (:colors state) (:artwork %)))
       (map #(select-keys % [:artwork :artwork-sizes]))
       (distinct)))

(defn attrs
  "The attributes of the part of the page that the artwork of `x` colours
  in the `state`: its seed as the --seed of its style, or none while it's
  unknown."
  [state x]
  (if-let [s (seed state x)]
    {:style {:--seed s}}
    {}))

(comment
  (wanted {:route  {:name :route/queue}
           :player {:playback {:artwork "https://example.org/a.jpg"}}})
  ;; => ({:artwork "https://example.org/a.jpg"})
  #_.)
