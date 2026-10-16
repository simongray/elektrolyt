(ns dk.simongray.podcast-app.main
  "The app in the browser: the store of its state, the effects and the render
  loop.

  The views dispatch actions as data, and the router dispatches one for
  each navigation. Nexus expands them into effects, which alone change the
  store, and each change of the store renders the page again."
  (:require [clojure.string :as str]
            [dk.simongray.podcast :as podcast]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast-app.library :as app-library]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.ui :as ui]
            [nexus.registry :as nxr]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.history :as rfh]
            [replicant.dom :as r]))

(defonce ^{:doc "The whole state of the app, which only effects change."}
  store
  (atom {}))

(defn navigated
  "The actions of the navigation to the `route` from the `state`: keep the
  route, move the focus to the heading of a page that replaces another, and
  search for the query of a Search page that has no search for it yet.

  The page that loads first keeps the focus at the start of the document,
  and so does a page that only changes its query."
  [state route]
  (let [q (get-in route [:query-params :q])]
    (cond-> [[:store/assoc :route route]]
      (and (contains? state :route)
           (not (routes/same-page? route (:route state))))
      (conj [:page/focus-heading])

      (and (= :route/search (:name route))
           (not (str/blank? q))
           (not (get-in state [:searches q])))
      (conj [:search/run q]))))

(defn submitted
  "The actions of the search for the words or the link `q` that the listener
  submitted: search at once, even for a query that was searched before, and
  put the query in the URL.

  The search comes first, so that the navigation finds it under way and
  doesn't start another."
  [_ q]
  (let [q (str/trim (str q))]
    (if (str/blank? q)
      [[:route/push :route/search]]
      [[:search/run q] [:route/push :route/search {} {:q q}]])))

(defn subscribing
  "The `state` while the feed at `url` is fetched for a subscription."
  [state url]
  (update state :subscribing (fnil conj #{}) url))

(defn subscribed
  "The `state` once the feed at `url` is `fetched` for a subscription to the
  show `title`: subscribed when the fetch has the feed, and with the error
  of the fetch or the subscription otherwise.

  Either way, the announcement says how it went."
  [state url title {:keys [error] :as fetched}]
  (let [[lib error] (if error
                      [nil error]
                      (try
                        [(library/subscribe (:library state) url fetched) nil]
                        (catch :default e
                          [nil {:message (ex-message e)}])))]
    (cond-> (update state :subscribing disj url)
      lib   (-> (assoc :library lib)
                (update :subscribe-errors dissoc url)
                (assoc :announcement (str "Subscribed to " title)))
      error (-> (assoc-in [:subscribe-errors url] error)
                (assoc :announcement (str "Couldn't subscribe to " title))))))

(defn search!
  "Search for the query `q`, and keep what's found so far under the query in
  the :searches of the state, through `dispatch`: the feeds at a link, or
  else the shows that the directories have."
  [dispatch q]
  (let [found! #(dispatch [[:store/assoc-in [:searches q] %]])]
    (found! {:status :searching :results [] :failed []})
    (-> (if (search/link? q)
          (.then (discovery/find-feeds! q {:send app-library/send})
                 #(found! (search/feeds->search %)))
          (directory/search! q {:send          app-library/send
                                :on-results-fn #(found! (search/shows->search %))}))
        (.catch #(found! {:status  :done
                          :results []
                          :failed  []
                          :error   (ex-message %)})))))

(defn subscribe!
  "Subscribe the library of the `store` to the show `title` at `url`, and
  save the library."
  [store url title]
  (app-library/persist!)
  (swap! store subscribing url)
  (-> (podcast/fetch! url {:max-pages 20 :send app-library/send})
      (.then #(swap! store subscribed url title %))
      (.then #(app-library/save! (:library %)))
      (.then #(when (:error %)
                (js/console.error "The library wasn't saved" (clj->js %))))))

(nxr/register-system->state! deref)

(nxr/register-placeholder! :form/field
  (fn [{:replicant/keys [dom-event]} field-name]
    (some-> dom-event .-target (js/FormData.) (.get field-name))))

(nxr/register-expansion! :route/navigated navigated)

(nxr/register-expansion! :search/submit submitted)

(nxr/register-effect! :store/assoc
  (fn [_ store k v]
    (swap! store assoc k v)))

(nxr/register-effect! :store/assoc-in
  (fn [_ store path v]
    (swap! store assoc-in path v)))

(nxr/register-effect! :event/prevent-default
  (fn [{:keys [dispatch-data]} _]
    (some-> (:replicant/dom-event dispatch-data) .preventDefault)))

(nxr/register-effect! :page/focus-heading
  (fn [_ _]
    (some-> (js/document.querySelector "main h1") .focus)))

(nxr/register-effect! :route/push
  (fn [_ _ route-name & [path-params query-params]]
    (rfe/push-state route-name path-params query-params)))

(nxr/register-effect! :search/run
  (fn [{:keys [dispatch]} _ q]
    (search! dispatch q)))

(nxr/register-effect! :library/subscribe
  (fn [_ store url title]
    (subscribe! store url title)))

(defn routed-click?
  "Whether the `router` takes over the `click` on the `anchor` to `uri`: when
  reitit would, unless a handler of the app has already prevented the
  click's default action, as the skip link does."
  [router ^goog.events.BrowserEvent click anchor uri]
  (and (not (.. click getBrowserEvent -defaultPrevented))
       (rfh/ignore-anchor-click? router click anchor uri)))

(defn start-router!
  "Follow the paths of the window's history, which dispatches the navigation
  to the current one at once."
  []
  (rfe/start! routes/router
              (fn [match _]
                (nxr/dispatch store {} [[:route/navigated
                                         (routes/match->route match)]]))
              {:use-fragment         false
               :ignore-anchor-click? routed-click?}))

(defn restore-library!
  "Restore the library that this browser saved into the store.

  A library that doesn't restore stays out of the store rather than be
  replaced by a new one, which the next save would write over the old."
  []
  (-> (app-library/restore!)
      (.then #(nxr/dispatch store {} [[:store/assoc :library %]]))
      (.catch #(js/console.error "The library didn't restore" %))))

(defn render!
  "Render the page for `state` into the element of the app, and give the
  window the page's title."
  [state]
  (r/render (js/document.getElementById "app") (ui/page state))
  (set! js/document.title (ui/document-title state)))

(defn ^:dev/after-load reload!
  "Start the router again with the routes and views that shadow-cljs just
  reloaded, which renders the page."
  []
  (start-router!))

(defn ^:export init
  "Start the app: send the actions of the views to Nexus, render the page at
  each change of the store, follow the window's history, and restore the
  library."
  []
  (r/set-dispatch! #(nxr/dispatch store %1 %2))
  (add-watch store ::render (fn [_ _ _ state] (render! state)))
  (start-router!)
  (restore-library!))
