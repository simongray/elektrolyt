(ns dk.simongray.podcast-app.main
  "The app in the browser: the store of its state, the effects and the render
  loop.

  The views dispatch actions as data, and the router dispatches one for
  each navigation. Nexus expands them into effects, which alone change the
  store, and each change of the store renders the page again."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.image :as image]
            [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.play-nice.page.tabs :as tabs]
            [dk.simongray.podcast.app.episode :as episode]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.app.worker :as worker]
            [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.audio :as audio]
            [dk.simongray.podcast-app.cache :as app-cache]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.inbox :as inbox]
            [dk.simongray.podcast-app.library :as app-library]
            [dk.simongray.podcast-app.opml :as opml]
            [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.refresh :as refresh]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.settings :as settings]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.ui.episode :as episode-ui]
            [nexus.registry :as nxr]
            [reitit.core :as reitit]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.history :as rfh]
            [replicant.dom :as r]))

(defonce ^{:doc "The whole state of the app, which only effects change."}
  store
  (atom {}))

(defn navigated
  "The actions of the navigation to the `route` from the `state`: keep the
  route, move the focus to the heading of a page that replaces another,
  search for the query of a Search page that has no search for it yet,
  load the show of a Show page or an Episode page, ask the browser how it
  keeps the library for the Settings page, and show the part of the page
  that the URL's fragment names.

  The page that loads first keeps the focus at the start of the document,
  and so does a page that only changes its query, unless the fragment
  names a part of it."
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

      (#{:route/show :route/episode} (:name route))
      (conj [:show/load (get-in route [:path-params :guid]) url false])

      (= :route/settings (:name route))
      (conj [:storage/check])

      ;; last, once the page shows, and so after the focus moves to its
      ;; heading, which this moves on
      (:fragment route)
      (conj [:page/scroll-to (:fragment route)]))))

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
  submitted in the `state`: put the query in the URL, which searches when
  the state has no search for it, and search again at once when the one in
  the state failed somewhere.

  A search that went well stays, since the cache would give the same
  lists. One that's run again comes first, so that the navigation finds it
  under way and doesn't start another."
  [state q]
  (let [q    (str/trim (str q))
        push [:route/push :route/search {} {:q q}]]
    (cond
      (str/blank? q)                                [[:route/push :route/search]]
      (search/failed? (get-in state [:searches q])) [[:search/run q] push]
      :else                                         [push])))

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

(defn index-credentials
  "The credentials of the Podcast Index that the page gives podcast-clj in
  the `state`: none of its own, since the app's server signs the requests,
  or nil when the server can't."
  [state]
  (when (get-in state [:server :podcastindex?])
    {}))

(defn search-directory!
  "Search the directory `source` for the words `q`, and keep its list under
  the query in the :searches of the state, through `dispatch`, with the
  `index` credentials for the Podcast Index.

  A list that the cache kept less than a day ago answers at once, unless
  the listener asks to `retry?`. A new list goes into the cache, and for a
  directory that fails, the cache's list shows with its date."
  [dispatch q source index retry?]
  (let [listed! #(dispatch [[:store/assoc-in [:searches q :lists source] %]])
        k       (search/cache-key source q)
        ask!    (fn [kept]
                  (-> (directory/search! q {:send        app-cache/send
                                            :sources     [source]
                                            :credentials index})
                      (.then search/shows->list)
                      (.catch #(search/failed (ex-message %)))
                      (.then (fn [l]
                               (if (= :failed (:status l))
                                 (listed! (search/stale-list l kept))
                                 (do (listed! l)
                                     (app-cache/put! app-cache/searches k l)))))))]
    (listed! {:status :searching})
    (.then (app-cache/fetch! app-cache/searches k)
           (fn [kept]
             (if (and (not retry?) (search/fresh? kept (js/Date.)))
               (listed! (:value kept))
               (ask! kept))))))

;; Each directory answers in a list of its own, so that no answer reorders
;; or replaces what another showed, as one merged list did when the others
;; arrived (web.dev, Cumulative Layout Shift)
;; https://web.dev/articles/cls
(defn search!
  "Search for the query `q`, and keep what's found under the query in the
  :searches of the state, through `dispatch`: the feeds at a link, or else
  the shows that each of the directories `sources` has, in a list of its
  own. With the `index` credentials, the Podcast Index can be among them."
  [dispatch q sources index]
  (if (search/link? q)
    (let [found! #(dispatch [[:store/assoc-in [:searches q] %]])]
      (found! {:status :searching :results []})
      (-> (discovery/find-feeds! q {:send app-library/send :index index})
          (.then #(found! (search/feeds->search %)))
          (.catch #(found! {:status :done :results [] :error (ex-message %)}))))
    (do (dispatch [[:store/assoc-in [:searches q] (search/searching sources)]])
        (doseq [source sources]
          (search-directory! dispatch q source index false)))))

(defn canonical-path
  "The path that the Show page or the Episode page of the `state` should
  have, or nil before the title is known: with the slug of the title, and
  for a show that the library keeps, its key there and no URL of its feed,
  since the library knows the URL."
  [{{:keys [name path-params query-params]} :route :as state}]
  (let [guid (:guid path-params)
        url  (:url query-params)]
    (case name
      :route/show
      (when-let [title (:title (show/page state guid))]
        (if-let [k (some-> (:library state) (show/library-key guid url))]
          (routes/show-href k title)
          (routes/show-href guid title url)))

      :route/episode
      (when-let [{:keys [guid item-guid title url]} (episode-ui/route-page state)]
        (routes/episode-href guid item-guid title url (episode-ui/start-time state)))

      nil)))

(defn canonicalize!
  "Write the canonical-path of the page of the `state` into the address
  bar, in place of the URL as the link gave it, e.g. without a slug. It
  replaces the URL in the history, which adds no entry, and leaves the
  route of the state as it was, so that the focus doesn't move."
  [state]
  (when-let [path (canonical-path state)]
    (when (not= path (str js/location.pathname js/location.search))
      (.replaceState js/history nil "" path))))

(defn whole-feed
  "The whole feed at `url` that the Show page loaded into the `state`, as a
  fetch gives it, with its validators, or nil. A newer feed that waits for
  the listener comes first, since a subscription should have the newest."
  [state url]
  (some (fn [{:keys [feed validators newer] :as shown}]
          (when (and feed (= url (:url shown)))
            (or (:fetched newer) (assoc validators :feed feed))))
        (vals (:shows state))))

(defn subscribe!
  "Subscribe the library of the `store` to the show `title` at `url`, and
  save the library. A feed that the Show page has whole isn't fetched
  again, and one that it hasn't is fetched in the worker."
  [store url title]
  (app-library/persist!)
  (swap! store subscribing url)
  (-> (if-let [fetched (whole-feed @store url)]
        (js/Promise.resolve fetched)
        (worker/fetch! @app-library/web-worker url {:max-pages 20}))
      (.then #(swap! store subscribed url title %))
      ;; a show subscribed to from its page loses the URL of its feed from
      ;; the address bar, since the library knows it now
      (.then #(do (canonicalize! %) %))
      (.then (fn [_] (app-library/save!)))
      (.then #(when (:error %)
                (js/console.error "The library wasn't saved" (clj->js %))))))

(declare load-show!)

(defn look-up-show!
  "Find the feed of the show `guid` in the cache, or else in the Podcast
  Index through the app's server, and load the show into the `store` as
  load-show! does with `preview?`. A show that the index doesn't know is
  :not-found. When `fresh?`, only the index is asked, e.g. once the feed
  at the URL from the cache is gone."
  [store guid preview? fresh?]
  (let [not-found! (fn [_]
                     (swap! store update-in [:shows guid] assoc :status :not-found))
        found!     (fn [url cached?]
                     (swap! store update-in [:shows guid] assoc
                            :url url :status nil :cached-url? cached?)
                     (load-show! store guid url preview?))
        ask!       #(-> (directory/call! :podcastindex :lookup {:id guid}
                                         {:send app-cache/send :credentials {}})
                        (.then (fn [[show]]
                                 (if-let [url (some-> show resolve/self-url)]
                                   (do (app-cache/put! app-cache/feed-urls guid url)
                                       (found! url false))
                                   (not-found! nil))))
                        (.catch not-found!))]
    (swap! store update-in [:shows guid] assoc :status :looking-up)
    (if fresh?
      (ask!)
      (.then (app-cache/fetch! app-cache/feed-urls guid)
             (fn [kept]
               (if-let [url (:value kept)]
                 (found! url true)
                 (ask!)))))))

(defn show-failed!
  "Note in the `store` that the whole feed of the show `guid` failed with
  `error`, or look the guid up in the Podcast Index again when the feed
  is gone from the URL that the cache had for it."
  [store guid error]
  (if (and (get-in @store [:shows guid :cached-url?])
           (#{404 410} (:status error)))
    (do (swap! store update-in [:shows guid] dissoc :url :cached-url?)
        (look-up-show! store guid false true))
    (swap! store update-in [:shows guid] assoc :status :failed :error error)))

(defn show-fetched!
  "Put the whole feed of the show `guid` at `url` that the worker
  `fetched` into the :shows of the `store`, and keep it in the cache."
  [store guid url fetched]
  (canonicalize! (swap! store show/whole guid url fetched (js/Date.) true))
  (app-cache/put! app-cache/shows (show/cache-key url) (show/kept fetched)))

(defn fetch-show!
  "Fetch the show `guid` at `url` into the :shows of the `store`: the start
  of its feed, and the whole feed too unless `preview?`. Each is fetched
  once, however often it's asked for, but a whole feed whose fetch failed
  is fetched again."
  [store guid url preview?]
  (let [status   (get-in @store [:shows guid :status])
        arrived! #(canonicalize! (swap! store show/arrived guid url %1 %2))]
    (when (nil? status)
      (swap! store update-in [:shows guid] assoc :url url :status :previewing)
      (.then (discovery/fetch-head! url {:items show/page-size :send app-library/send})
             #(some-> (:feed %) (arrived! false))))
    (when (and (not preview?) (contains? #{nil :previewing :previewed :failed} status))
      (swap! store update-in [:shows guid] assoc :url url :status :loading)
      ;; in the worker, since a feed of 20 MB takes a third of a second or
      ;; more to parse; the start of the feed already shows the first
      ;; episodes, parsed here, since it's small
      (-> (worker/fetch! @app-library/web-worker url {:max-pages 20})
          (.then (fn [{:keys [feed error] :as fetched}]
                   (if feed
                     (show-fetched! store guid url fetched)
                     (show-failed! store guid error))))
          (.catch #(show-failed! store guid {:message (ex-message %)}))))))

;; RFC 9110, sections 13.1.1 and 13.1.3: the validators of the kept feed
;; ask whether it changed, and a 304 says it didn't
(defn check-show!
  "Fetch the feed of the show `guid` at `url` in the `store` again, with
  the validators of the one from the cache, once a visit and only when
  it's past its time. A 304 keeps it for another while, and a new feed
  goes into the cache and onto the page, or waits when it brings new
  episodes."
  [store guid url]
  (let [{:keys [feed validators checked?]} (get-in @store [:shows guid])
        keep! #(app-cache/put! app-cache/shows (show/cache-key url) (show/kept %))]
    (when (and feed (not checked?))
      (swap! store update-in [:shows guid] assoc :checked? true :checking? true)
      (-> (worker/fetch! @app-library/web-worker url
                         (merge {:max-pages 20}
                                (select-keys validators [:etag :last-modified])))
          (.then (fn [{:keys [status] :as fetched}]
                   (cond
                     (:feed fetched)
                     (do (swap! store show/refetched guid url fetched (js/Date.))
                         (keep! fetched))

                     ;; the time of the old fetch has passed, so only the
                     ;; 304's own counts
                     (= 304 status)
                     (keep! (merge {:url url :feed feed}
                                   (dissoc validators :not-before)
                                   (show/validators fetched))))))
          (.catch #(js/console.warn "The show wasn't checked" %))
          (.finally #(swap! store update-in [:shows guid] dissoc :checking?))))))

(defn open-show!
  "Open the show `guid` at `url` into the `store` from the feed that the
  cache keeps, or else fetch it as fetch-show! does with `preview?`. A kept
  feed past its time is checked once the page is open, i.e. unless every
  ask while the cache was read was a `preview?`."
  [store guid url preview?]
  (swap! store update-in [:shows guid] assoc
         :url url :status :opening :whole? (not preview?))
  (.then (app-cache/fetch! app-cache/shows (show/cache-key url))
         (fn [kept]
           (let [whole? (get-in @store [:shows guid :whole?])]
             (swap! store update-in [:shows guid] dissoc :whole?)
             (if-let [fetched (:value kept)]
               (let [fresh? (show/fresh? fetched (:at kept) (js/Date.))]
                 (canonicalize! (swap! store show/whole guid url fetched (:at kept) fresh?))
                 (when whole?
                   (check-show! store guid url)))
               (do (swap! store update-in [:shows guid] assoc :status nil)
                   (fetch-show! store guid url (not whole?))))))))

(defn load-show!
  "Load the show `guid` at `url` into the :shows of the `store`, unless the
  library keeps it: from the cache, or else the start of its feed and the
  whole feed too unless `preview?`, as open-show! has it.

  Without a URL, the cache or the Podcast Index finds the feed by the
  guid, when the app's server signs the requests to it."
  [store guid url preview?]
  (let [state       @store
        url         (or url (get-in state [:shows guid :url]))
        status      (get-in state [:shows guid :status])
        in-library? #(some-> (:library state) (show/library-key guid %))]
    (when (and (nil? url) (nil? status) (index-credentials state) (not (in-library? nil)))
      (look-up-show! store guid preview? false))
    (when (and url (not (in-library? url)))
      (case status
        nil      (open-show! store guid url preview?)
        :opening (when-not preview?
                   (swap! store assoc-in [:shows guid :whole?] true))
        :done    (when-not preview?
                   (check-show! store guid url))
        (fetch-show! store guid url preview?)))))

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

(defn read-files!
  "Read the files of the episode `id`, as its `view` names them, into the
  :episodes of the `store`, once: its chapters file and its media file, for
  the chapters and the duration. The transcript waits for the listener."
  [store id view]
  (when-not (get-in @store [:episodes id :status])
    (swap! store assoc-in [:episodes id :status] :reading)
    (-> (episode/read-files! view {:send app-library/send :except #{:transcript}})
        (.then #(swap! store update-in [:episodes id] assoc :status :read :result %))
        ;; e.g. the chapters of a file, for the player and the lock screen
        (.then #(when (= id (get-in % [:player :id]))
                  (swap! store assoc-in [:player :playback] (audio/playback % id))
                  (audio/lock-screen! store)))
        (.catch #(swap! store update-in [:episodes id] assoc :status :read
                        :error {:message (ex-message %)})))))

(defn read-transcript!
  "Read the transcript of the episode `id`, as its `view` names it, into
  the :episodes of the `store`, once."
  [store id view]
  (when-not (get-in @store [:episodes id :transcript-status])
    (swap! store assoc-in [:episodes id :transcript-status] :reading)
    (-> (episode/read-files! view {:send app-library/send :except #{:chapters :media}})
        (.then #(swap! store update-in [:episodes id] assoc
                       :transcript-status :read :transcript (:transcript %)))
        (.catch #(swap! store update-in [:episodes id] assoc
                        :transcript-status :read
                        :transcript {:error {:message (ex-message %)}})))))

(nxr/register-system->state! deref)

(nxr/register-placeholder! :form/field
  (fn [{:replicant/keys [dom-event]} field-name]
    (some-> dom-event .-target (js/FormData.) (.get field-name))))

(nxr/register-placeholder! :event/target-number
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event .-target .-value js/parseFloat)))

(nxr/register-placeholder! :event/target-keyword
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event .-target .-value not-empty keyword)))

(nxr/register-placeholder! :event/target-checked
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event .-target .-checked)))

(nxr/register-placeholder! :event/range-seek
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event controls/range-seek)))

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
  (fn [{:keys [dispatch]} store q]
    (let [index (index-credentials @store)]
      (search! dispatch q (search/sources (:library @store) (some? index)) index))))

(nxr/register-effect! :search/retry
  (fn [{:keys [dispatch]} store q source]
    (search-directory! dispatch q source (index-credentials @store) true)))

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

;; the button goes as the list takes the new episodes, so the focus moves
;; to the heading of the list
(nxr/register-effect! :show/newer
  (fn [_ store guid]
    (swap! store show/newer-shown guid)
    (some-> (js/document.getElementById "episodes") .focus)))

;; dispatched as the page of the episode mounts, so it changes the store
;; once the render is done
(nxr/register-effect! :episode/read-files
  (fn [_ store id view]
    (js/queueMicrotask #(read-files! store id view))))

(nxr/register-effect! :episode/read-transcript
  (fn [{:keys [dispatch-data]} store id view]
    (when (some-> (:replicant/dom-event dispatch-data) .-target .-open)
      (read-transcript! store id view))))

;; dispatched as the player's element mounts, so it changes the store once
;; the render is done
(nxr/register-effect! :player/mounted
  (fn [{:keys [dispatch-data]} store]
    (let [node (:replicant/node dispatch-data)]
      (js/queueMicrotask #(audio/mounted! store node)))))

(nxr/register-effect! :player/unmounted
  (fn [_ _]
    (@audio/detach!)))

(nxr/register-effect! :player/play
  (fn [_ store playable t]
    (audio/play! store playable t)))

;; the controls act on the playback that the player shows, wherever it
;; plays (see audio/act!)
(nxr/register-effect! :player/resume
  (fn [_ store]
    (audio/resume-shown! store)))

(nxr/register-effect! :player/pause
  (fn [_ store]
    (audio/pause-shown! store)))

(nxr/register-effect! :player/press
  (fn [_ store details]
    (audio/act! store details #(audio/press! store details))))

(nxr/register-effect! :player/drag
  (fn [_ store details]
    (audio/drag-shown! store details)))

(nxr/register-effect! :player/chapter
  (fn [_ store direction]
    (audio/act! store {:action :chapter :direction direction}
                #(audio/chapter! store direction))))

(nxr/register-effect! :player/ended
  (fn [_ store]
    (audio/ended! store)))

(nxr/register-effect! :player/rate
  (fn [_ store rate]
    (audio/act! store {:action :rate :rate rate} #(audio/rate! store rate))))

(nxr/register-effect! :player/volume
  (fn [_ store level]
    (audio/volume-shown! store level)))

(nxr/register-effect! :player/mute
  (fn [_ store muted?]
    (audio/mute-shown! store muted?)))

(nxr/register-effect! :player/expand
  (fn [_ store expanded?]
    (swap! store assoc-in [:player :expanded?] expanded?)))

(nxr/register-effect! :page/focus
  (fn [_ _ id]
    (some-> (js/document.getElementById id) .focus)))

;; CSSOM View, scrollIntoView: a heading at the start of a row that
;; scrolls sideways, which brings its column into view, and at the top of
;; the page where it's out of sight, e.g. further down a stack, since focus
;; alone scrolls nothing where a part of the element shows
;; https://drafts.csswg.org/cssom-view/#dom-element-scrollintoview
(nxr/register-effect! :page/show
  (fn [_ _ id]
    (when-let [^js heading (js/document.getElementById id)]
      (let [box   (.getBoundingClientRect heading)
            seen? (and (>= (.-top box) 0) (<= (.-bottom box) js/window.innerHeight))]
        (.scrollIntoView heading #js {:block  (if seen? "nearest" "start")
                                      :inline "start"})
        (.focus heading #js {:preventScroll true})))))

;; HTML, scroll to the fragment, which a navigation by pushState leaves
;; undone: the start of the element at the top, as far as the page
;; scrolls. The focus moves there too, as the skip link moves it.
;; https://html.spec.whatwg.org/multipage/browsing-the-web.html#scroll-to-fragid
(nxr/register-effect! :page/scroll-to
  (fn [_ _ id]
    (when-let [^js target (js/document.getElementById id)]
      (.scrollIntoView target #js {:block "start" :inline "nearest"})
      (.focus target #js {:preventScroll true}))))

(nxr/register-effect! :dialog/open
  (fn [_ _ id]
    (when-let [dialog (js/document.getElementById id)]
      (set! (.-returnValue dialog) "")
      (.showModal dialog))))

(def library-view-key
  "The key of the listener's choices for the Library page in localStorage,
  since the layout that suits a screen belongs to this device."
  "dk.simongray.podcast-app.library-view")

(defn saved-library-view
  "The listener's choices for the Library page that this browser keeps, or
  nil."
  []
  (try
    (some-> (.getItem js/localStorage library-view-key) reader/read-string)
    (catch :default _ nil)))

(nxr/register-effect! :library/view
  (fn [_ store k v]
    (swap! store assoc-in [:library-view k] v)
    (try
      (.setItem js/localStorage library-view-key (pr-str (:library-view @store)))
      (catch :default _ nil))))

(nxr/register-effect! :library/refresh
  (fn [_ store]
    (refresh/refresh! store true)))

(defn unsubscribed
  "The `state` once the listener unsubscribed from the show `title` at
  `url`."
  [state url title]
  (if-let [k (some-> (:library state) (show/library-key nil url))]
    (-> state
        (update :library library/unsubscribe k)
        (assoc :announcement (str "Unsubscribed from " title)))
    state))

;; the page of the show stays, now as one outside the library, by the URL
;; of its feed, which it gets in its address too
(nxr/register-effect! :library/unsubscribe
  (fn [_ store url title]
    (let [guid (get-in @store [:route :path-params :guid])]
      (swap! store unsubscribed url title)
      (app-library/save!)
      (if-let [s (routes/slug title)]
        (rfe/replace-state :route/titled-show {:guid guid :slug s} {:url url})
        (rfe/replace-state :route/show {:guid guid} {:url url})))))

(defn profile-changed!
  "Change the state of the `store` with `f` of it and the `args`, a change
  of the listener's profile, e.g. of the queue, and save the library."
  [store f & args]
  (apply swap! store f args)
  (audio/save! store))

(nxr/register-effect! :library/new-episodes
  (fn [_ store k choice]
    (when choice
      (swap! store update :library library/update-profile
             profile/configure k (profile/new-episodes-settings choice))
      (audio/save! store))))

(nxr/register-effect! :settings/prefer
  (fn [_ store k v]
    (when (or (boolean? v) (keyword? v) (js/Number.isFinite v))
      (profile-changed! store settings/preferred {k v}))))

(nxr/register-effect! :settings/new-episodes
  (fn [_ store choice]
    (when choice
      (profile-changed! store settings/defaulted choice))))

(nxr/register-effect! :settings/apply-to-all
  (fn [_ store]
    (profile-changed! store settings/applied-to-all)))

(nxr/register-effect! :settings/search-source
  (fn [_ store source on?]
    (profile-changed! store settings/turned source (boolean on?))))

(nxr/register-effect! :settings/move-source
  (fn [_ store source to]
    (profile-changed! store settings/moved-source source to)))

;; the input is emptied, so that choosing the same file again imports it
;; again
(nxr/register-effect! :opml/import
  (fn [{:keys [dispatch-data]} store]
    (when-let [input (some-> (:replicant/dom-event dispatch-data) .-target)]
      (when-let [file (some-> input .-files (aget 0))]
        (opml/import! store file)
        (set! (.-value input) "")))))

(nxr/register-effect! :opml/export
  (fn [_ store]
    (opml/export! @store)))

(nxr/register-effect! :storage/check
  (fn [_ store]
    (.then (app-library/storage!) #(swap! store assoc :storage %))))

(nxr/register-effect! :storage/persist
  (fn [_ store]
    (-> (or (app-library/persist!) (js/Promise.resolve false))
        (.then (fn [granted?]
                 (when-not granted?
                   (swap! store assoc :settings-status
                          {:group :this-browser
                           :text  "The browser didn't agree to keep it."}))
                 (app-library/storage!)))
        (.then #(swap! store assoc :storage %)))))

(defn cleared
  "The `state` once the cache is empty, or not, by `error`: without the
  searches and shows that the tab had too, so that Back doesn't bring them
  back, and with a status that says how it went."
  [state error]
  (if error
    (assoc state :settings-status
           {:group :this-browser
            :text  "The searches and shows couldn't be cleared."})
    (-> (dissoc state :searches :shows)
        (assoc :settings-status {:group :this-browser
                                 :text  "Cleared the searches and shows."}))))

(nxr/register-effect! :cache/clear
  (fn [_ store]
    (-> (app-cache/clear!)
        (.then #(swap! store cleared nil))
        (.catch (fn [e]
                  (js/console.error "The cache wasn't cleared" e)
                  (swap! store cleared e)))
        (.then #(.then (app-library/storage!) (fn [s] (swap! store assoc :storage s)))))))

(nxr/register-effect! :queue/add
  (fn [_ store {:keys [id] :as playable} title next?]
    (try
      (swap! store #(-> (assoc % :library (audio/added % playable))
                        (queue/added id title next?)))
      (audio/save! store)
      (catch :default e
        (js/console.error "The episode couldn't be queued" e)))))

(nxr/register-effect! :queue/move
  (fn [_ store id to]
    (profile-changed! store queue/moved id to)))

(nxr/register-effect! :queue/remove
  (fn [_ store id]
    (profile-changed! store queue/removed id)))

(nxr/register-effect! :queue/undo
  (fn [_ store]
    (profile-changed! store queue/undone)))

(nxr/register-effect! :inbox/dismiss
  (fn [_ store id]
    (profile-changed! store inbox/dismissed id)))

(nxr/register-effect! :inbox/dismiss-all
  (fn [_ store]
    (profile-changed! store inbox/all-dismissed)))

(nxr/register-effect! :inbox/queue-all
  (fn [_ store]
    (profile-changed! store inbox/all-queued)))

(nxr/register-effect! :inbox/undo
  (fn [_ store]
    (profile-changed! store inbox/undone)))

;; once the dialog has closed, since the page is inert while it's open
(nxr/register-effect! :queue/clear-closed
  (fn [{:keys [dispatch-data]} store focus-id]
    (when-let [dialog (some-> (:replicant/dom-event dispatch-data) .-target)]
      (when (= "clear" (.-returnValue dialog))
        (set! (.-returnValue dialog) "")
        (profile-changed! store queue/cleared)
        (some-> (js/document.getElementById focus-id) .focus)))))

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
  "Restore the library that this browser saved into the store, as the
  replica of this tab, which catches up with what the other tabs and the
  worker save.

  A library that doesn't restore stays out of the store rather than be
  replaced by a new one, which the next save would write over the old."
  []
  (-> (app-library/open! store)
      ;; the title of a show that only the library keeps is known now
      (.then #(canonicalize! @store))
      (.catch #(js/console.error "The library didn't restore" %))))

(defn load-server-info!
  "Read what the app's server offers from /api/server into the :server of
  the store, e.g. whether it signs for the Podcast Index, as a promise that
  settles either way."
  []
  (-> (js/fetch "/api/server")
      (.then #(.text %))
      (.then (fn [text]
               (nxr/dispatch store {} [[:store/assoc :server
                                        (transit/read (transit/reader :json) text)]])))
      (.catch #(js/console.error "The server didn't say what it offers" %))))

(def theme-key
  "The key in localStorage of a copy of the colours that the listener
  chose, which public/theme.js applies before the page first renders,
  rather than show the device's until the library is restored."
  "dk.simongray.podcast-app.theme")

(def contrast-key
  "The key in localStorage of a copy of the contrast that the listener
  chose, which public/theme.js applies as it does the colours."
  "dk.simongray.podcast-app.contrast")

(defn attributed!
  "Give the document's root the attribute `attr` with the value `v`, or
  else none, and keep a copy of it in this browser under the `key`, as it
  changes."
  [attr key v]
  (let [root js/document.documentElement]
    (when (not= v (.getAttribute root attr))
      (if v
        (.setAttribute root attr v)
        (.removeAttribute root attr))
      (try
        (if v
          (.setItem js/localStorage key v)
          (.removeItem js/localStorage key))
        (catch :default _ nil)))))

(defn themed!
  "Give the document the colours and the contrast that the listener chose
  in the `state`, as its data-theme and its data-contrast, or else none,
  for the device's."
  [state]
  (attributed! "data-theme" theme-key (settings/theme state))
  (attributed! "data-contrast" contrast-key (settings/contrast state)))

;; HTML, the theme-color meta name: a colour for the browser's own bars,
;; e.g. Chrome's on Android, and for the title bar of an installed app. It
;; takes no var(), so it gets the shell's background, the bars' colour, as
;; the browser computed it, in hex. Safari on iOS 26 reportedly ignores it.
;; https://html.spec.whatwg.org/multipage/semantics.html#meta-theme-color
;; https://benfrain.com/ios26-safari-theme-color-tab-tinting-with-fixed-position-elements/
(defn theme-colored!
  "Give the page's theme-color the colour of the bars of the `shell`, as
  they are now."
  [shell]
  (when-let [meta (js/document.querySelector "meta[name=theme-color]")]
    (when-let [c (some-> (.-backgroundColor (js/getComputedStyle shell)) cc/hex)]
      (when (not= c (.-content meta))
        (set! (.-content meta) c)))))

(defn render!
  "Render the page for `state` into the element of the app, and give the
  window the page's title and the listener's colours."
  [state]
  (r/render (js/document.getElementById "app") (ui/page state))
  (set! js/document.title (ui/document-title state))
  (themed! state)
  (some-> (js/document.querySelector ".shell") theme-colored!))

(defn kept-value
  "The colours of an artwork in the entry `kept` of the cache, or nil for
  none, or for colours kept before they had a :dominant colour."
  [kept]
  (when (contains? (:value kept) :dominant)
    (:value kept)))

(defn image-colors
  "A promise of the colours of the image in the bytes `b` that colors.cljc
  keeps: its :seed and its :dominant colour, as hex, or nil where it can't
  be decoded."
  [b]
  (image/then (image/colours b)
              (fn [{:keys [seed background]}]
                {:seed     (some-> seed cc/hex)
                 :dominant (some-> background cc/hex)})))

(defn load-colors!
  "Work out the colours of the artwork `x`, as colors/wanted names it, into
  the :colors of the `store`: from the cache, or else from the image,
  whose colours the cache keeps then. An image that doesn't come leaves
  the artwork without colours until the page loads again."
  [store {:keys [artwork] :as x}]
  (-> (app-cache/fetch! app-cache/colors artwork)
      (.then (fn [kept]
               (or (kept-value kept)
                   (-> (app-library/send {:url (colors/source x) :as :bytes})
                       (.then #(image-colors (:body %)))
                       (.then (fn [v]
                                (when v
                                  (app-cache/put! app-cache/colors artwork v))
                                v))))))
      (.catch (fn [e]
                (js/console.warn "The colours of the artwork weren't worked out" e)
                nil))
      (.then #(swap! store assoc-in [:colors artwork] (or % {:seed nil})))))

(def colors-at-once
  "How many artworks' colours are worked out at once, so that a long list,
  e.g. the Library, doesn't send all its images through the proxy at once."
  4)

(defn colored!
  "Work out the colours of the artworks that the page of the `state` wants
  and doesn't have yet, into the `store`, a few at once, the first first."
  [store state]
  (let [loading (count (filter #(= :loading (:status %)) (vals (:colors state))))
        xs      (take (- colors-at-once loading) (colors/wanted state))]
    (when (seq xs)
      (swap! store update :colors merge (zipmap (map :artwork xs) (repeat {:status :loading})))
      (doseq [x xs]
        (load-colors! store x)))))

(defn kept-colors!
  "A promise that the :colors of the `store` have what the cache keeps of
  the artwork that the page at the window's location wants, before it
  renders, which comes to nothing for artwork that it doesn't keep."
  [store]
  (let [route  (routes/match->route (reitit/match-by-path routes/router js/location.pathname))
        wanted (colors/wanted (assoc @store :route route))]
    (-> (js/Promise.all (into-array (map #(app-cache/fetch! app-cache/colors (:artwork %)) wanted)))
        (.then (fn [kept]
                 (swap! store update :colors merge
                        (into {} (keep (fn [[x k]] (some->> (kept-value k) (vector (:artwork x)))))
                              (map vector wanted kept))))))))

(defn ^:dev/after-load reload!
  "Start the router again with the routes and views that shadow-cljs just
  reloaded, which renders the page."
  []
  (start-router!))

(defn ^:export init
  "Start the app: send the actions of the views to Nexus, follow the
  playback that the tabs share, restore the library, and once the server
  has said what it offers, put the current episode in the player, render
  the page at each change of the store and follow the window's history.
  Tell the library where the player is, and save it, when the page is
  hidden.

  The first page waits for both, since what it shows can depend on them,
  e.g. a search asks the Podcast Index too when the server signs for it,
  only the Podcast Index finds a show by its guid alone, and a show that
  the library keeps needs neither the index nor a fetch. It waits for the
  colours that the cache keeps of its artwork too, so that it opens in
  them rather than turns to them."
  []
  (r/set-dispatch! #(nxr/dispatch store %1 %2))
  (tabs/watch! player/element-name #(audio/shared! store %))
  (.addEventListener js/document "visibilitychange"
                     #(if (= "hidden" js/document.visibilityState)
                        (audio/hidden! store)
                        (refresh/refresh-if-due! store)))
  (.addEventListener js/window "pagehide" #(audio/hidden! store))
  ;; the bars' colour once it has moved to the next one
  (.addEventListener js/document "transitionend"
                     #(when (.. % -target -classList (contains "shell"))
                        (theme-colored! (.-target %))))
  (audio/volume-locked! store)
  (swap! store assoc :library-view (saved-library-view))
  (-> (js/Promise.all #js [(restore-library!) (load-server-info!)])
      (.finally #(swap! store audio/restored (audio/kept-position)))
      (.then #(kept-colors! store) #(kept-colors! store))
      (.finally (fn []
                  (add-watch store ::render (fn [_ _ _ state] (render! state)))
                  (add-watch store ::colors (fn [_ _ _ state] (colored! store state)))
                  (start-router!)
                  (refresh/refresh-if-due! store)))))
