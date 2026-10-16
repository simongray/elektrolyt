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
            [dk.simongray.podcast-app.show :as show]
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
  route, move the focus to the heading of a page that replaces another,
  search for the query of a Search page that has no search for it yet, and
  load the show of a Show page.

  The page that loads first keeps the focus at the start of the document,
  and so does a page that only changes its query."
  [state route]
  (let [{:keys [q url]} (:query-params route)]
    (cond-> [[:store/assoc :route route]]
      (and (contains? state :route)
           (not (routes/same-page? route (:route state))))
      (conj [:page/focus-heading])

      (and (= :route/search (:name route))
           (not (str/blank? q))
           (not (get-in state [:searches q])))
      (conj [:search/run q])

      (= :route/show (:name route))
      (conj [:show/load (get-in route [:path-params :guid]) url false]))))

(defn more-shown
  "The actions of the listener's ask for more of the `total` episodes of the
  show `guid` in the `state`: list another page of them, and say how many
  are listed now."
  [state guid total]
  (let [shown (min total (+ (get-in state [:shows guid :shown] show/page-size)
                            show/page-size))]
    [[:store/assoc-in [:shows guid :shown] shown]
     [:store/assoc :announcement (str "Showing " shown " of " total " episodes")]]))

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

(defn canonical-path
  "The path that the Show page of the `state` should have, or nil before
  the show's title is known: with the slug of its title, and for a show
  that the library keeps, its key there and no URL of its feed, since the
  library knows the URL."
  [{{:keys [path-params query-params]} :route :as state}]
  (let [guid (:guid path-params)
        url  (:url query-params)]
    (when-let [title (:title (show/page state guid))]
      (if-let [k (some-> (:library state) (show/library-key guid url))]
        (routes/show-href k title)
        (routes/show-href guid title url)))))

(defn canonicalize!
  "Write the canonical-path of the Show page of the `state` into the
  address bar, in place of the URL as the link gave it, e.g. without a slug.
  It replaces the URL in the history, which adds no entry, and leaves the
  route of the state as it was, so that the focus doesn't move."
  [{{:keys [name]} :route :as state}]
  (when (= :route/show name)
    (when-let [path (canonical-path state)]
      (when (not= path (str js/location.pathname js/location.search))
        (.replaceState js/history nil "" path)))))

(defn whole-feed
  "The whole feed at `url` that the Show page loaded into the `state`, or
  nil."
  [state url]
  (some (fn [{:keys [feed] :as shown}]
          (when (and feed (= url (:url shown)))
            feed))
        (vals (:shows state))))

(defn subscribe!
  "Subscribe the library of the `store` to the show `title` at `url`, and
  save the library. A feed that the Show page has whole isn't fetched
  again."
  [store url title]
  (app-library/persist!)
  (swap! store subscribing url)
  (-> (if-let [feed (whole-feed @store url)]
        (js/Promise.resolve feed)
        (podcast/fetch! url {:max-pages 20 :send app-library/send}))
      (.then #(swap! store subscribed url title %))
      ;; a show subscribed to from its page loses the URL of its feed from
      ;; the address bar, since the library knows it now
      (.then #(do (canonicalize! %) %))
      (.then #(app-library/save! (:library %)))
      (.then #(when (:error %)
                (js/console.error "The library wasn't saved" (clj->js %))))))

(defn load-show!
  "Load the show `guid` at `url` into the :shows of the `store`, unless the
  library keeps it: the start of its feed, and the whole feed too unless
  `preview?`. Each is fetched once, however often it's asked for, but a
  whole feed whose fetch failed is fetched again."
  [store guid url preview?]
  (let [state    @store
        url      (or url (get-in state [:shows guid :url]))
        status   (get-in state [:shows guid :status])
        send     app-library/send
        arrived! #(canonicalize! (swap! store show/arrived guid url %1 %2))
        failed!  #(swap! store update-in [:shows guid] assoc
                         :status :failed :error %)]
    (when (and url (not (some-> (:library state) (show/library-key guid url))))
      (when (nil? status)
        (swap! store update-in [:shows guid] assoc :url url :status :previewing)
        (.then (discovery/fetch-head! url {:items show/page-size :send send})
               #(some-> (:feed %) (arrived! false))))
      (when (and (not preview?) (contains? #{nil :previewing :previewed :failed} status))
        (swap! store update-in [:shows guid] assoc :url url :status :loading)
        ;; TODO: parse the whole feed in a web worker, as the inspector of
        ;; podcast-clj refreshes its feeds, since a feed of 20 MB takes a
        ;; second or more to parse on the main thread, where the page waits
        ;; for it. The start of the feed already shows the first episodes.
        (-> (podcast/fetch! url {:max-pages 20 :send send})
            (.then (fn [{:keys [feed error]}]
                     (if feed
                       (arrived! feed true)
                       (failed! error))))
            (.catch #(failed! {:message (ex-message %)})))))))

(defonce ^{:doc "The timeout of the prefetch that a pointer resting on a
  link to a show starts, which leaving the link cancels."}
  prefetch-timeout
  (atom nil))

(defn prefetch-soon!
  "Fetch the start of the feed of the show `guid` at `url` into the `store`
  in 65 ms, unless the pointer leaves the link to it first."
  [store guid url]
  (some-> @prefetch-timeout js/clearTimeout)
  (reset! prefetch-timeout (js/setTimeout #(load-show! store guid url true) 65)))

(nxr/register-system->state! deref)

(nxr/register-placeholder! :form/field
  (fn [{:replicant/keys [dom-event]} field-name]
    (some-> dom-event .-target (js/FormData.) (.get field-name))))

(nxr/register-expansion! :route/navigated navigated)

(nxr/register-expansion! :search/submit submitted)

(nxr/register-expansion! :show/more more-shown)

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

(nxr/register-effect! :show/load
  (fn [_ store guid url preview?]
    (load-show! store guid url preview?)
    (canonicalize! @store)))

(nxr/register-effect! :show/prefetch-soon
  (fn [_ store guid url]
    (prefetch-soon! store guid url)))

(nxr/register-effect! :show/prefetch-cancel
  (fn [_ _]
    (some-> @prefetch-timeout js/clearTimeout)
    (reset! prefetch-timeout nil)))

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
      ;; the title of a show that only the library keeps is known now
      (.then #(canonicalize! @store))
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
