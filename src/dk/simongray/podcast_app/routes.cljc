(ns dk.simongray.podcast-app.routes
  "The pages of the app by name and path, and the paths of the links to them."
  (:require [reitit.core :as reitit]))

(def router
  "The router of the app's paths, whose routes are named :route/…"
  (reitit/router
   [["/"         :route/queue]
    ["/inbox"    :route/inbox]
    ["/library"  :route/library]
    ["/search"   :route/search]
    ["/settings" :route/settings]]))

(defn href
  "The path of the route named `route-name`, with its `path-params`."
  ([route-name]
   (href route-name nil))
  ([route-name path-params]
   (-> (reitit/match-by-name router route-name path-params)
       (reitit/match->path))))

(defn match->route
  "The route of the reitit `match` as it's kept in the state: its :name and
  :path-params, or nil for a path of no route."
  [{:keys [data path-params] :as match}]
  (when match
    {:name        (:name data)
     :path-params path-params}))

(comment
  (href :route/library)
  ;; => "/library"
  (match->route (reitit/match-by-path router "/inbox"))
  ;; => {:name :route/inbox, :path-params {}}
  #_.)
