(ns dk.simongray.podcast-app.ui.lists
  "Episodes as the rows of lists: the artwork, the title as a link to the
  episode's page, and a line of facts, as the queue, the inbox and the
  picks show them."
  (:require [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.facts :as facts-ui]))

(defn episode-href
  "The path of the page of the episode `id` of the library `lib` titled
  `title`, with the URL of its feed when the library keeps it for the
  queue alone."
  [lib [feed-guid item-guid :as id] title]
  (routes/episode-href feed-guid item-guid title
                       (when-not (profile/subscribed? (:profile lib) feed-guid)
                         (get-in lib [:feeds feed-guid :url]))))

(defn row
  "The `episode` of the library of the `state` as a row: its artwork, its
  title in a heading of `tag`, e.g. :h3, as a link to its page, and the
  `facts` in a line."
  [{:keys [library] :as state} {:keys [id title] :as episode} tag facts]
  (list
   (artwork/img episode 48 (colors/placeholder state episode))
   [tag [:a {:href (episode-href library id title)} title]]
   (facts-ui/line facts)))
