(ns dk.simongray.podcast-app.server-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [cognitect.transit :as transit]
            [dk.simongray.podcast-app.server :as server]
            [io.pedestal.connector.test :refer [response-for]]))

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
                                      :headers page))))))

(deftest proxy-test
  (testing "the proxy needs a URL"
    (is (= 400 (:status (response-for connector :get "/proxy")))))
  (testing "the proxy answers GET and HEAD alone"
    (is (= 405 (:status (response-for connector :post "/proxy"))))))

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

(deftest refusals-test
  (testing "a name other than localhost is refused"
    (is (= 403 (:status (response-for connector :get "/"
                                      :headers {"host" "evil.example"})))))
  (testing "a write from another site is refused"
    (is (= 403 (:status (response-for connector :post "/proxy"
                                      :headers {"sec-fetch-site"
                                                "cross-site"}))))))
