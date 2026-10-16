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
  "The route of the reitit `match` as it's kept in the state: its :name,
  :path-params and :query-params, or nil for a path of no route."
  [{:keys [data path-params query-params] :as match}]
  (when match
    {:name         (:name data)
     :path-params  path-params
     :query-params (or query-params {})}))

(defn same-page?
  "Whether the routes `a` and `b` are the same page, perhaps with other
  query parameters, e.g. the Search page of another query."
  [a b]
  (= (dissoc a :query-params) (dissoc b :query-params)))

(comment
  (href :route/library)
  ;; => "/library"
  (match->route (reitit/match-by-path router "/inbox"))
  ;; => {:name :route/inbox, :path-params {}, :query-params {}}
  #_.)
