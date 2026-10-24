(ns dk.simongray.elektrolyt.server-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [cognitect.transit :as transit]
            [dk.simongray.elektrolyt.server :as server]
            [io.pedestal.connector.test :refer [response-for]]
            [taoensso.telemere :as tel])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(def connector
  (server/connector {:port 8400 :client-dir "public"}))

(def page
  "The Accept header of a browser that opens a page."
  {"accept" "text/html,application/xhtml+xml,*/*;q=0.8"})

(deftest client-side-routes-test
  (let [index (slurp "public/index.html")]
    (testing "a client-side route gives a browser the app's index.html"
      (let [{:keys [status headers body]} (response-for connector :get "/"
                                                        :headers page)]
        (is (= 200 status))
        (is (= index body))
        (is (str/includes? (get headers "Content-Security-Policy")
                           "object-src 'none'")))
      (let [{:keys [status body]} (response-for connector :get "/search?q=x"
                                                :headers page)]
        (is (= 200 status))
        (is (= index body))))
    (testing "also at a path that ends in a slash"
      (doseq [path ["/library/" "/shows/abc/"]]
        (let [{:keys [status body]} (response-for connector :get path
                                                  :headers page)]
          (is (= 200 status) path)
          (is (= index body) path))))
    (testing "but not a script that asks for anything other than HTML"
      (is (= 404 (:status (response-for connector :get "/library"
                                        :headers {"accept" "*/*"})))))))

(deftest files-test
  (testing "a file is served with the type of its name"
    (let [{:keys [status headers]} (response-for connector :get
                                                 "/css/app.css")]
      (is (= 200 status))
      (is (str/starts-with? (get headers "Content-Type") "text/css"))))
  (testing "a missing file is a 404, even for a browser"
    (is (= 404 (:status (response-for connector :get "/js/missing.js"
                                      :headers page)))))
  (testing "and so is a dotfile or a dot folder, with a slash or without"
    (doseq [path ["/.env" "/.git" "/.git/" "/.git/config" "/%2Egit/"]]
      (is (= 404 (:status (response-for connector :get path :headers page)))
          path))))

(deftest a-file-that-a-build-wrote-again
  (let [dir  (.toFile (Files/createTempDirectory "client" (make-array FileAttribute 0)))
        file (io/file dir "app.js")
        c    (server/connector {:port 8400 :client-dir (.getPath dir)})
        get! #(response-for c :get "/app.js" :headers %)]
    (try
      (spit file "old")
      (let [fetched (get! {})
            etag    (get-in fetched [:headers "ETag"])
            stamp   (.lastModified file)]
        (testing "a file has an ETag of its bytes, and no date in whole seconds"
          (is (string? etag))
          (is (nil? (get-in fetched [:headers "Last-Modified"]))))
        (testing "a file that the browser has is a 304"
          (is (= 304 (:status (get! {"if-none-match" etag})))))
        ;; written in the same second, or put back by an older build
        (spit file "new")
        (.setLastModified file stamp)
        (testing "a file that a build wrote again is sent again"
          (is (= [200 "new"] ((juxt :status :body) (get! {"if-none-match" etag})))))
        (testing "also to a browser that kept the date of the file it has"
          (is (= 200 (:status (get! {"if-modified-since" "Wed, 01 Jan 2031 00:00:00 GMT"}))))))
      (finally
        (run! io/delete-file (reverse (file-seq dir)))))))

(deftest proxy-test
  (testing "the proxy needs a URL"
    (is (= 400 (:status (response-for connector :get "/proxy")))))
  (testing "the proxy answers GET and HEAD alone"
    (is (= 405 (:status (response-for connector :post "/proxy"))))))

(deftest artwork-test
  (testing "the thumbnails need a URL and a width from the list"
    (is (= 400 (:status (response-for connector :get "/artwork"))))
    (is (= 400 (:status (response-for connector :get "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=65")))))
  (testing "and they're for the app's own pages"
    (is (= 403 (:status (response-for connector :get "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=64"
                                      :headers {"sec-fetch-site" "cross-site"}))))))

(defn read-transit
  "The value of the transit JSON `s`."
  [s]
  (transit/read (transit/reader (io/input-stream (.getBytes ^String s "UTF-8")) :json)))

(deftest server-info-test
  (testing "the page reads what the server offers as transit"
    (let [{:keys [status headers body]} (response-for connector :get "/api/server")]
      (is (= 200 status))
      (is (= "application/transit+json" (get headers "Content-Type")))
      (is (= {:podcastindex? false} (read-transit body)))))
  (testing "and the server signs for the Podcast Index with its credentials"
    (let [signing (server/connector {:port         8400
                                     :client-dir   "public"
                                     :podcastindex {:key "K" :secret "S"}})]
      (is (= {:podcastindex? true}
             (read-transit (:body (response-for signing :get "/api/server"))))))))

(deftest secrets-test
  (let [file (doto (java.io.File/createTempFile "secrets" ".edn")
               (.deleteOnExit))]
    (spit file (pr-str {:podcastindex {:key "the-key" :secret "the-secret"}
                        :other        {:token "not this one"}}))
    (let [credentials (server/podcastindex-credentials (str file))]
      (testing "the credentials of the Podcast Index, and nothing else"
        (is (= #{:key :secret} (set (keys credentials))))
        (is (= "the-key" (:key credentials))))
      (testing "with the secret hidden from what prints"
        (is (not (str/includes? (pr-str credentials) "the-secret")))
        (is (str/includes? (pr-str credentials) "#<secret>"))))
    (is (nil? (server/podcastindex-credentials "no/such/file.edn")))
    (is (nil? (server/podcastindex-credentials nil)))))

(defn read-logged
  "The credentials in a temporary secrets file that holds the text `s`, the
  path of the file and the signals that reading it logs."
  [s]
  (let [file (str (doto (java.io.File/createTempFile "secrets" ".edn")
                    (.deleteOnExit)
                    (spit s)))
        {:keys [value signals]} (tel/with-signals true
                                  (server/podcastindex-credentials file))]
    {:credentials value :file file :signals signals}))

(deftest secrets-that-cant-be-read-test
  (doseq [s ["{:podcastindex {:key \"K\""
             "#unknown/tag {}"
             "{:podcastindex {:key \"K\" :secret 1secret}}"]]
    (let [{:keys [credentials file signals]} (read-logged s)]
      (testing "a file that isn't EDN means no credentials, and a warning that names it"
        (is (nil? credentials) s)
        (is (= [:warn] (map :level signals)) s)
        (is (str/includes? (:msg_ (first signals)) file) s))
      (testing "but not what the reader quotes of it, e.g. a secret without quotes"
        (is (not (str/includes? (pr-str signals) "1secret")) s)))))

(deftest credentials-without-a-key-or-a-secret-test
  (doseq [podcastindex ["abc" {} {:key "K"} {:secret "S"}
                        {:key "K" :secret " "} {:key "" :secret "S"}
                        {:key "K" :secret 5}]]
    (let [{:keys [credentials file signals]} (read-logged (pr-str {:podcastindex podcastindex}))]
      (testing "credentials that the Podcast Index would refuse count as none, with a warning"
        (is (nil? credentials) (pr-str podcastindex))
        (is (= [:warn] (map :level signals)) (pr-str podcastindex))
        (is (str/includes? (:msg_ (first signals)) file)))
      (testing "a warning that doesn't show the secret"
        (is (not (str/includes? (pr-str signals) "\"S\""))))))
  (testing "while a file without the Podcast Index logs nothing"
    (is (= {:credentials nil :signals nil}
           (dissoc (read-logged (pr-str {:other {:token "T"}})) :file)))))

(deftest refusals-test
  (testing "a name other than localhost is refused"
    (is (= 403 (:status (response-for connector :get "/"
                                      :headers {"host" "evil.example"})))))
  (testing "a write from another site is refused"
    (is (= 403 (:status (response-for connector :post "/proxy"
                                      :headers {"sec-fetch-site"
                                                "cross-site"}))))))
