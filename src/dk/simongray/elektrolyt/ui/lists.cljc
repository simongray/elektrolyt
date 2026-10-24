(ns dk.simongray.elektrolyt.ui.lists
  "Episodes as the rows of a list, with artwork, a linked title and a line
  of facts, and the link after the start of a list to the whole of it."
  (:require [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.routes :as routes]))

(defn queue-feed-url
  "The feed URL of the show `feed-guid` that the library `lib` keeps only
  for the queue, for links to the show's pages, or nil for a show that the
  listener subscribes to."
  [lib feed-guid]
  (when-not (profile/subscribed? (:profile lib) feed-guid)
    (get-in lib [:feeds feed-guid :url])))

(defn episode-href
  "The path of the page of the episode `id` titled `title` in the library
  `lib`."
  [lib [feed-guid item-guid :as id] title]
  (routes/episode-href feed-guid item-guid title (queue-feed-url lib feed-guid)))

(defn show-href
  "The path of the page of the show `feed-guid` titled `title` in the
  library `lib`."
  [lib feed-guid title]
  (routes/show-href feed-guid title (queue-feed-url lib feed-guid)))

(defn thumbnail
  "The artwork of `x` for a row of a list in `state`, loaded lazily, with
  its dominant colour behind it while it loads."
  [state x]
  (artwork/img x 48 (merge {:loading "lazy"} (colors/placeholder state x))))

(defn linked-row
  "A row of a list: the artwork `img` and the `title` in a heading `tag`
  in the language `lang`, both linked to `href`, and the `facts` in a
  line."
  [href img tag title lang facts]
  (list
   (artwork/linked href title img)
   [tag [:a (cond-> {:href href} lang (assoc :lang lang)) title]]
   (facts-ui/line facts)))

(defn row
  "The `episode` in the library of `state` as a row, with its artwork and
  its title in a heading `tag`, both linked to its page, and the `facts`
  in a line."
  [{:keys [library] :as state} {:keys [id title] :as episode} tag facts]
  (linked-row (episode-href library id title) (thumbnail state episode) tag title nil facts))

(defn more
  "The line after the start of a list that links with the `label` to the
  whole list on the route `route-name`, followed by its `summary`, e.g.
  \"The whole queue: 3 episodes, 2h\"."
  [route-name label summary]
  [:p.list-more [:a {:href (routes/href route-name)} label] ": " summary])
