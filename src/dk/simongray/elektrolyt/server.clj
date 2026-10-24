(ns dk.simongray.elektrolyt.server
  "The app's server, which serves the app's files, the proxy through which
  the app fetches feeds, and thumbnails of artwork, all on one origin.

  It listens on localhost alone, since it checks no credentials and the
  proxy fetches any public URL. With the Podcast Index's credentials, the
  proxy signs the requests to it, so that the secret stays on the server."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [dk.simongray.podcast.directory.podcastindex :as podcastindex]
            [dk.simongray.podcast.util.client :as client]
            [dk.simongray.podcast.util.ring :as ring]
            [dk.simongray.elektrolyt.server.artwork :as artwork]
            [dk.simongray.wary-fetch.proxy :as proxy]
            [dk.simongray.wary-fetch.secret :as secret]
            [io.pedestal.connector :as conn]
            [io.pedestal.http.jetty :as jetty]
            [io.pedestal.http.ring-middlewares :as ring-middlewares]
            [io.pedestal.http.secure-headers :as secure-headers]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.service.interceptors :as interceptors]
            [io.pedestal.service.resources :as resources]
            [taoensso.telemere :as tel])
  (:import [java.io ByteArrayOutputStream]
           [java.nio.file Files]
           [java.security MessageDigest]
           [java.util HexFormat]
           [java.util.concurrent Semaphore TimeUnit]))

(defn refused
  "The 403 response that refuses a request with `message`."
  [message]
  (ring/text-response 403 message))

(def localhost-only
  "An interceptor that refuses a request whose Host header names anything
  but this machine, so that a web page can't reach the server by DNS
  rebinding."
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
  another site's form that posts here. Clients other than browsers send no
  Sec-Fetch-Site, so their writes get through."
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
  "An interceptor that rewrites a path that ends in a slash to its
  folder's index.html, so that the content type comes from the file name."
  (interceptor/interceptor
   {:name  ::folders
    :enter (fn [context]
             (update context :request
                     (fn [{:keys [uri] :as request}]
                       (if (str/ends-with? uri "/")
                         (let [index (str uri "index.html")]
                           (assoc request :uri index :path-info index))
                         request))))}))

;; The rules of connect-history-api-fallback, which webpack's dev server
;; uses, but without */*, so that a script that fetches a missing path
;; gets a 404
(defn client-side-route?
  "Whether the Ring `request` is for a page of the app rather than a file:
  a GET or a HEAD of a path without a file extension, from a client that
  accepts HTML."
  [{:keys [request-method uri headers] :as request}]
  (boolean (and (#{:get :head} request-method)
                (not (re-find #"\.[^/]*$" uri))
                (some->> (get headers "accept") (re-find #"text/html")))))

(defn index-fallback
  "An interceptor that answers a request for a page of the app with the
  index.html in `client-dir` when nothing else has, so that the app's
  router can show the page. It looks at the request as it arrived, before
  folders adds index.html to a path that ends in a slash."
  [client-dir]
  (interceptor/interceptor
   {:name  ::index-fallback
    :enter #(assoc % ::request (:request %))
    :leave (fn [{::keys [request] :keys [response] :as context}]
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
;; judge a file fresh by itself and run an older build's code
(def revalidated
  "An interceptor that has the browser check with the server before it uses
  a cached file, which costs a 304 when the file hasn't changed."
  (interceptor/interceptor
   {:name  ::revalidated
    :leave (fn [{:keys [response] :as context}]
             (cond-> context
               response (assoc-in [:response :headers "Cache-Control"]
                                  "no-cache")))}))

(defn file-tag
  "The ETag of the bytes in `file`: the start of their SHA-256, quoted."
  [^java.io.File file]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (Files/readAllBytes (.toPath file)))]
    (str "\"" (subs (.formatHex (HexFormat/of) digest) 0 32) "\"")))

;; RFC 9110, sections 8.8.3 and 13.2.2: Last-Modified counts whole
;; seconds, so If-Modified-Since would get a 304 for a file rebuilt within
;; the same second, or for an older build put back
(defn tagged
  "An interceptor that gives a response with a file in `client-dir` an
  ETag of its bytes in place of its Last-Modified."
  [client-dir]
  (interceptor/interceptor
   {:name  ::tagged
    :leave (fn [{:keys [request response] :as context}]
             (let [file (io/file client-dir (str/replace (:uri request) #"^/+" ""))]
               (cond-> context
                 (and (= 200 (:status response)) (.isFile file))
                 (update-in [:response :headers]
                            #(-> (dissoc % "Last-Modified")
                                 (assoc "ETag" (file-tag file)))))))}))

(defn client-routes
  "The routes of the app's files in `client-dir`, each served with an ETag
  of its bytes, though never a dotfile."
  [client-dir]
  ;; read on each request, since shadow-cljs rewrites the files. The
  ;; interceptors leave in reverse order, so the ETag is set before
  ;; not-modified checks it
  (resources/file-routes {:file-root    (.getPath (io/file client-dir))
                          :cache?       false
                          :interceptors [no-dotfiles
                                         revalidated
                                         (ring-middlewares/content-type)
                                         (ring-middlewares/not-modified)
                                         (tagged client-dir)]}))

;; RFC 9209, sections 2 and 2.3.12: the proxy's own failure, which the
;; page's wary-fetch reads as one, rather than as the 503 of the site that
;; it asked, which would count against that site's feed
(def busy-proxy
  "The headers of a 503 from the proxy when it's busy."
  {"Retry-After"  "5"
   "Proxy-Status" "wary-fetch; error=connection_limit_reached; details=\"The server is busy\""})

(defn at-most
  "The Ring `handler` answering at most `n` requests at once. A request
  that waits `seconds` for its turn gets a 503 with the `busy` headers."
  [n seconds busy handler]
  (let [turns (Semaphore. n true)]
    (fn [request]
      (if (.tryAcquire turns (long seconds) TimeUnit/SECONDS)
        (try
          (handler request)
          (finally
            (.release turns)))
        (ring/text-response
         503 "The server is busy. Try again in a few seconds."
         busy)))))

(defn read-secrets
  "The EDN in the file `secrets`, or nil when there's no such file. It
  returns nil for a file that isn't EDN too, and logs a warning that names
  the file, so that the server still starts."
  [secrets]
  (when (and secrets (.isFile (io/file secrets)))
    (try
      (edn/read-string (slurp secrets))
      ;; without the exception, whose message can quote the text that the
      ;; reader stopped at, e.g. a secret without quotes
      (catch Exception _
        (tel/log! {:level :warn :id :server/secrets-failed
                   :data  {:file (str secrets)}}
                  (str "The secrets in " secrets " couldn't be read"))))))

(defn podcastindex-credentials
  "The Podcast Index's :key and :secret under :podcastindex in the EDN file
  `secrets`, or nil without them. The secret is hidden with wary-fetch, so
  that it prints as #<secret>.

  Each must be a string that isn't blank, since the Podcast Index refuses
  a request without them, and a :podcastindex that lacks one logs a
  warning that names the file."
  [secrets]
  (let [credentials (:podcastindex (read-secrets secrets))]
    (if (every? #(and (string? %) (not (str/blank? %)))
                ((juxt :key :secret) credentials))
      (-> (select-keys credentials [:key :secret])
          (update :secret secret/hide))
      (when (some? credentials)
        (tel/log! {:level :warn :id :server/credentials-incomplete
                   :data  {:file (str secrets)}}
                  (str "The Podcast Index needs a :key and a :secret in "
                       secrets))))))

(defn transit-json
  "The value `x` written as transit in JSON."
  [x]
  (let [out (ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json) x)
    (.toString out "UTF-8")))

(defn server-info
  "The handler of what the server offers the app, as transit: whether it
  has the credentials `podcastindex` to sign requests to the Podcast
  Index, as :podcastindex?."
  [podcastindex]
  (let [body (transit-json {:podcastindex? (some? podcastindex)})]
    (fn [_]
      {:status  200
       :headers {"Content-Type" "application/transit+json"}
       :body    body})))

(defn routes
  "The routes of the proxy, the thumbnails of artwork and /api/server,
  with the Podcast Index credentials `podcastindex`, or nil, and the
  thumbnails kept in `artwork-dir`. The proxy holds up to four bodies of
  32 MB at once, and the thumbnails' handler scales two artworks, so that
  together they fit in a heap of 320 MB."
  [podcastindex artwork-dir]
  (let [send       (if podcastindex
                     (podcastindex/sender podcastindex {:send client/send!})
                     client/send!)
        proxy      (at-most 4 30 busy-proxy (proxy/handler {:user-agent client/user-agent
                                                            :send       send}))
        ;; no at-most, which would hold up the cached thumbnails too, since
        ;; the handler limits its own jobs
        thumbnails (artwork/handler {:cache-dir artwork-dir})]
    #{["/proxy" :any proxy :route-name ::proxy]
      ["/artwork" :any thumbnails :route-name ::artwork]
      ["/api/server" :get (server-info podcastindex) :route-name ::server-info]}))

(defn connector
  "A Jetty connector of the server with the `settings` below, not yet
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
           ;; scripts from this server only, with eval for shadow-cljs's
           ;; development builds; images, media and the rest from any host;
           ;; no HSTS on plain HTTP
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
  - :secrets, an EDN file with the Podcast Index's credentials under
    :podcastindex, by default the file that PODCAST_APP_SECRETS names, if
    any
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
       (tel/log! {:level :info :id :server/started
                  :data  {:port port :podcastindex? (some? podcastindex)}}
                 (str "The app is on http://localhost:" port "/"
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
