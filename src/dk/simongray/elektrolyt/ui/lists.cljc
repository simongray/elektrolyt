(ns dk.simongray.elektrolyt.ui.lists
  "Episodes as the rows of lists: the artwork, the title as a link to the
  episode's page, and a line of facts, as the queue, the inbox, the picks
  and a show's episodes show them, and the line after a list that links
  to the whole of it."
  (:require [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.colors :as colors]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]))

(defn episode-href
  "The path of the page of the episode `id` of the library `lib` titled
  `title`, with the URL of its feed when the library keeps it for the
  queue alone."
  [lib [feed-guid item-guid :as id] title]
  (routes/episode-href feed-guid item-guid title
                       (when-not (profile/subscribed? (:profile lib) feed-guid)
                         (get-in lib [:feeds feed-guid :url]))))

(defn thumbnail
  "The artwork of `x` in a row of a list of the `state`, in the colour of
  its artwork while it loads, which loads once it's near the screen."
  [state x]
  (artwork/img x 48 (merge {:loading "lazy"} (colors/placeholder state x))))

(defn linked-row
  "A row of a list: the `img` of its artwork, and its `title` in a heading
  of `tag`, e.g. :h3, in the language `lang`, both a link to `href`, and
  the `facts` in a line."
  [href img tag title lang facts]
  (list
   (artwork/linked href img)
   [tag [:a (cond-> {:href href} lang (assoc :lang lang)) title]]
   (facts-ui/line facts)))

(defn row
  "The `episode` of the library of the `state` as a row: its artwork and
  its title in a heading of `tag`, e.g. :h3, both a link to its page, and
  the `facts` in a line."
  [{:keys [library] :as state} {:keys [id title] :as episode} tag facts]
  (linked-row (episode-href library id title) (thumbnail state episode) tag title nil facts))

(defn more
  "The line after the start of a list that links to the whole of it, on
  the page of the route named `route-name`, with the `label` of the link
  and the `summary` of the list after it, e.g. \"The whole queue: 3
  episodes, 2h\"."
  [route-name label summary]
  [:p.list-more [:a {:href (routes/href route-name)} label] ": " summary])
