(ns dk.simongray.elektrolyt.library.picks.finding
  "Finding the picks of the places that the page shows once it has
  painted, and keeping them in the store, so that no render finds them."
  (:require [dk.simongray.elektrolyt.episode.ui :as episode-ui]
            [dk.simongray.elektrolyt.library.picks :as picks]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [taoensso.telemere :as tel]))

(defonce ^{:doc "The key that the picks of each place are being found for,
  by place, so that a change of the state meanwhile doesn't find them
  again."}
  pending
  (atom {}))

(defn episode-page
  "The page of the episode that the page in `state` shows, or nil."
  [state]
  (when (= :route/episode (get-in state [:route :name]))
    (episode-ui/route-page state)))

(defn found!
  "Find the picks of each place that the page in `state` shows whose kept
  picks are of another key, once the page has painted, and keep them in
  `store` while that key is still the place's."
  [store state]
  (doseq [place (picks/shown-places state (episode-page state))
          :let  [k (picks/place-key state place)]
          :when (and (picks/stale? state place k)
                     (not (picks/same-key? k (get @pending place))))]
    (swap! pending assoc place k)
    (-> (runtime/painted!)
        (.then (fn [_]
                 (let [now @store]
                   (when (picks/same-key? k (picks/place-key now place))
                     (swap! store picks/with-found place k (picks/found now place))))))
        (.catch #(tel/log! {:level :warn :id :picks/failed :data {:place place} :error %}
                           "The picks weren't found"))
        (.finally (fn []
                    (swap! pending #(cond-> % (picks/same-key? k (get % place)) (dissoc place))))))))
