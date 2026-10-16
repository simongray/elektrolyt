(ns dk.simongray.podcast-app.server-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.server :as server]
            [io.pedestal.connector.test :refer [response-for]]))

(def connector
  (server/connector 8400 "public"))

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

(deftest refusals-test
  (testing "a name other than localhost is refused"
    (is (= 403 (:status (response-for connector :get "/"
                                      :headers {"host" "evil.example"})))))
  (testing "a write from another site is refused"
    (is (= 403 (:status (response-for connector :post "/proxy"
                                      :headers {"sec-fetch-site"
                                                "cross-site"}))))))
