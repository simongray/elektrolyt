(ns dk.simongray.podcast-app.server
  "The app's own server, which serves the app's files, the proxy through
  which the app fetches feeds, and small thumbnails of artwork, on one
  origin.

  It listens on localhost alone, since it checks no credentials and the
  proxy fetches any public URL for whoever reaches it. With the credentials
  of the Podcast Index in a file of secrets, the proxy signs the requests to
  it, so that the secret stays on the server. What the server offers, the
  page reads from /api/server, as transit."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [dk.simongray.podcast.artwork :as artwork]
            [dk.simongray.podcast.directory.podcastindex :as podcastindex]
            [dk.simongray.podcast.util.client :as client]
            [dk.simongray.podcast.util.ring :as ring]
            [dk.simongray.wary-fetch.proxy :as proxy]
            [dk.simongray.wary-fetch.secret :as secret]
            [io.pedestal.connector :as conn]
            [io.pedestal.http.jetty :as jetty]
            [io.pedestal.http.ring-middlewares :as ring-middlewares]
            [io.pedestal.http.secure-headers :as secure-headers]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.service.interceptors :as interceptors]
            [io.pedestal.service.resources :as resources])
  (:import [java.io ByteArrayOutputStream]
           [java.util.concurrent Semaphore TimeUnit]))

(defn refused
  "The answer to a request that the server refuses, with `message`."
  [message]
  (ring/text-response 403 message))

(def localhost-only
  "An interceptor that answers only requests to localhost, with or without
  a port.

  A web page that points a name of its own at this machine's address (DNS
  rebinding) gets a 403, since the server checks no credentials."
  (interceptor/interceptor
   {:name  ::localhost-only
    :enter (fn [{:keys [request] :as context}]
             (if (ring/addressed-to? request #{})
               context
               (let [host    (ring/host-name (get-in request [:headers "host"]))
                     message (str "The server doesn't answer to the name "
                                  host ".")]
                 (assoc context :response (refused message)))))}))

;; Fetch Metadata Request Headers, W3C Working Draft of 2021-03-29
(def same-origin-writes
  "An interceptor that refuses a write from a page of another origin, e.g.
  a form of another site that posts to this one. Apps that aren't browsers
  send no Sec-Fetch-Site, so their writes get through."
  (interceptor/interceptor
   {:name  ::same-origin-writes
    :enter (fn [{{:keys [headers request-method]} :request :as context}]
             (let [site (get headers "sec-fetch-site")]
               (if (and (#{"cross-site" "same-site"} site)
                        (not (#{:get :head :options} request-method)))
                 (let [message "The server takes no writes from other sites."]
                   (assoc context :response (refused message)))
                 context)))}))

(def folders
  "An interceptor ahead of the router that sends a path that ends in a
  slash to the index.html of its folder, so that the type of the answer
  comes from the name of the file."
  (interceptor/interceptor
   {:name  ::folders
    :enter (fn [context]
             (update context :request
                     (fn [{:keys [uri] :as request}]
                       (if (str/ends-with? uri "/")
                         (let [index (str uri "index.html")]
                           (assoc request :uri index :path-info index))
                         request))))}))

;; the rules of connect-history-api-fallback (webpack's dev server), but
;; without */*, so that a script that fetches a missing path gets a 404
(defn client-side-route?
  "Whether the Ring `request` asks for a page of the app rather than a
  file: a GET or a HEAD of a path without a file extension, from a client
  that accepts HTML, e.g. a browser that opens /search?q=x."
  [{:keys [request-method uri headers] :as request}]
  (boolean (and (#{:get :head} request-method)
                (not (re-find #"\.[^/]*$" uri))
                (some->> (get headers "accept") (re-find #"text/html")))))

(defn index-fallback
  "An interceptor that answers a request for a page of the app with the
  index.html of `client-dir`, when nothing else answered it. The app's own
  router then shows the page, e.g. when the browser reloads /search?q=x."
  [client-dir]
  (interceptor/interceptor
   {:name  ::index-fallback
    :leave (fn [{:keys [request response] :as context}]
             (let [index (io/file client-dir "index.html")
                   body  (when (= :get (:request-method request)) index)]
               (cond-> context
                 (and (nil? response)
                      (client-side-route? request)
                      (.isFile index))
                 (assoc :response
                        (ring/response 200 "text/html; charset=utf-8"
                                       body)))))}))

(def no-dotfiles
  "An interceptor that answers 404 for a file or folder whose name starts
  with a dot, e.g. a .env or a .git that a build left in the app's folder."
  (interceptor/interceptor
   {:name  ::no-dotfiles
    :enter (fn [{:keys [request] :as context}]
             (if (re-find #"(?i)/(\.|%2e)" (:uri request))
               (assoc context :response {:status  404
                                         :headers {}
                                         :body    "Not Found"})
               context))}))

;; RFC 9111, sections 4.2.2 and 5.2.2.4: without no-cache, a browser may
;; judge a file fresh by its Last-Modified alone and run the code of an
;; older build
(def revalidated
  "An interceptor that has the browser check with the server before it uses
  a cached file. The check costs a 304 when the file hasn't changed."
  (interceptor/interceptor
   {:name  ::revalidated
    :leave (fn [{:keys [response] :as context}]
             (cond-> context
               response (assoc-in [:response :headers "Cache-Control"]
                                  "no-cache")))}))

(defn client-routes
  "The routes of the app's files in `client-dir`. Each file has the type of
  its name, and a 304 when the browser has it. A dotfile isn't served."
  [client-dir]
  ;; read again on each request, since shadow-cljs rewrites them
  (resources/file-routes {:file-root    (.getPath (io/file client-dir))
                          :cache?       false
                          :interceptors [no-dotfiles
                                         revalidated
                                         (ring-middlewares/content-type)
                                         (ring-middlewares/not-modified)]}))

(defn at-most
  "The Ring `handler` answering at most `n` requests at once, e.g. so that
  the proxy holds no more than `n` bodies in memory. A request that waits
  `seconds` for its turn gets a 503 with a Retry-After."
  [n seconds handler]
  (let [turns (Semaphore. n true)]
    (fn [request]
      (if (.tryAcquire turns (long seconds) TimeUnit/SECONDS)
        (try
          (handler request)
          (finally
            (.release turns)))
        (ring/text-response
         503 "The server is busy. Try again in a few seconds."
         {"Retry-After" "5"})))))

(defn podcastindex-credentials
  "The :key and :secret of the Podcast Index under :podcastindex in the EDN
  file `secrets`, or nil without the file or the key there. The secret is
  hidden with wary-fetch's secret/hide, so that it prints as #<secret>."
  [secrets]
  (when (and secrets (.isFile (io/file secrets)))
    (some-> (edn/read-string (slurp secrets))
            (:podcastindex)
            (select-keys [:key :secret])
            (update :secret secret/hide))))

(defn transit-json
  "The value `x` written as transit in JSON."
  [x]
  (let [out (ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json) x)
    (.toString out "UTF-8")))

(defn server-info
  "The handler of what the server with the Podcast Index credentials
  `podcastindex` offers the app, as transit: whether it signs the requests
  to the Podcast Index, as :podcastindex?."
  [podcastindex]
  (let [body (transit-json {:podcastindex? (some? podcastindex)})]
    (fn [_]
      {:status  200
       :headers {"Content-Type" "application/transit+json"}
       :body    body})))

(defn routes
  "The routes of the proxy, the thumbnails of artwork and /api/server in
  Pedestal's set syntax, with the Podcast Index credentials `podcastindex`,
  or nil, and the thumbnails kept in `artwork-dir`. The proxy fetches four
  files at once, of 32 MB at most, as podcast-clj's server does, and signs
  the requests to the Podcast Index. The thumbnails are made four at a
  time too."
  [podcastindex artwork-dir]
  (let [send       (if podcastindex
                     (podcastindex/sender podcastindex {:send client/send!})
                     client/send!)
        proxy      (at-most 4 30 (proxy/handler {:user-agent client/user-agent
                                                 :send       send}))
        thumbnails (at-most 4 30 (artwork/handler {:cache-dir artwork-dir}))]
    #{["/proxy" :any proxy :route-name ::proxy]
      ["/artwork" :any thumbnails :route-name ::artwork]
      ["/api/server" :get (server-info podcastindex) :route-name ::server-info]}))

(defn connector
  "A Jetty connector of the server with the `settings` below; not yet
  started.

  - :port, on localhost
  - :client-dir, the folder of the app's files
  - :podcastindex, the credentials of the Podcast Index, or nil
  - :artwork-dir, the folder of the thumbnails of artwork, or nil to keep
    none"
  [{:keys [port client-dir podcastindex artwork-dir] :as settings}]
  (let [policy {:object-src "'none'" :script-src "'self' 'unsafe-eval'"}]
    (-> (conn/default-connector-map "localhost" port)
        (conn/with-interceptors
          [interceptors/not-found
           ;; scripts from this server only, with eval for the development
           ;; builds of shadow-cljs; art, media and the rest from any host;
           ;; and no HSTS on plain HTTP
           (secure-headers/secure-headers
            {:content-security-policy-settings policy
             :hsts-settings                    nil})
           localhost-only
           same-origin-writes
           (index-fallback client-dir)
           folders])
        (conn/with-routes (routes podcastindex artwork-dir)
                          (client-routes client-dir))
        (jetty/create-connector nil))))

(defonce ^{:doc "The running server's connector, or nil when stopped."}
  server
  (atom nil))

(defn start!
  "Start the server with the `opts` below, unless it's running.

  - :port, 8400 by default
  - :client-dir, the folder of the app's files, public/ by default
  - :secrets, an EDN file with the credentials of the Podcast Index under
    :podcastindex, by default the file that the environment variable
    PODCAST_APP_SECRETS names, if any
  - :artwork-dir, the folder of the thumbnails of artwork, .cache/artwork
    by default"
  ([]
   (start! {}))
  ([{:keys [port client-dir secrets artwork-dir]
     :or   {port        8400
            client-dir  "public"
            secrets     (System/getenv "PODCAST_APP_SECRETS")
            artwork-dir ".cache/artwork"}}]
   (when-not @server
     (let [podcastindex (podcastindex-credentials secrets)]
       (reset! server (conn/start! (connector {:port         port
                                               :client-dir   client-dir
                                               :podcastindex podcastindex
                                               :artwork-dir  artwork-dir})))
       (println (str "The app is on http://localhost:" port "/"
                     (when podcastindex ", with the Podcast Index")))))
   @server))

(defn stop!
  "Stop the server."
  []
  (when-let [connector @server]
    (conn/stop! connector)
    (reset! server nil)))

(comment
  (start!)
  (stop!)
  #_.)
