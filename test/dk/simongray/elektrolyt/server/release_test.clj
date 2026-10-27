(ns dk.simongray.elektrolyt.server.release-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dk.simongray.elektrolyt.server.release :as release])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(deftest a-name-of-the-content
  (let [named (release/named-by-content "js/main.js" "x")]
    (is (re-matches #"js/main\.[0-9a-f]{16}\.js" named))
    (is (release/content-named? named))
    (is (= named (release/named-by-content "js/main.js" "x")) "the same for the same content")
    (is (not= named (release/named-by-content "js/main.js" "y")) "and another for another")
    (is (not (release/content-named? "js/main.js")))))

(deftest the-references-of-a-file
  (is (= ["\"/js/shared.js\"" "\"shared.js\""] (release/references "js/worker.js" "js/shared.js"))
      "by its path, or by its name in the same folder")
  (is (= ["\"/js/shared.js\""] (release/references "index.html" "js/shared.js")))
  (is (= "importScripts(\"shared.1.js\");"
         (release/renamed-in "importScripts(\"shared.js\");" "js/worker.js" {"js/shared.js" "js/shared.1.js"}))))

(deftest files-named-in-the-order-of-their-references
  (let [files {"js/shared.js" "shared"
               "js/worker.js" "importScripts(\"shared.js\");"
               "js/main.js"   "new Worker(\"/js/worker.js\")"
               "css/look.css" "a {}"
               "css/app.css"  "@import url(\"look.css\") layer(look);"}
        named (release/content-names files)
        name  #(first (named %))]
    (is (= (set (keys files)) (set (keys named))))
    (is (= (str "importScripts(\"" (subs (name "js/shared.js") 3) "\");") (second (named "js/worker.js")))
        "a worker that imports the shared module by its new name")
    (is (= (str "new Worker(\"/" (name "js/worker.js") "\")") (second (named "js/main.js")))
        "and a page that starts it by its own")
    (is (str/includes? (second (named "css/app.css")) (subs (name "css/look.css") 4)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (release/content-names {"a.js" "\"/b.js\"" "b.js" "\"/a.js\""}))
        "but not files that refer to each other")))

(deftest a-release-named-by-content
  (let [dir      (.toFile (Files/createTempDirectory "release" (make-array FileAttribute 0)))
        write!   (fn [path text] (io/make-parents (io/file dir path)) (spit (io/file dir path) text))
        release! (fn [main]
                   (write! "index.html" "<link href=\"/css/app.css\"><script src=\"/js/main.js\"></script>")
                   (write! "css/app.css" "a {}")
                   (write! "js/main.js" main)
                   (release/name-by-content! dir))]
    (try
      (let [first-names (release! "1")
            index       (slurp (io/file dir "index.html"))]
        (testing "copies of the scripts and stylesheets under names of their content"
          (is (= 2 (count first-names)))
          (is (every? #(.isFile (io/file dir %)) first-names))
          (is (.isFile (io/file dir "js/main.js")) "beside the files under their own names"))
        (testing "which index.html names"
          (is (every? #(str/includes? index (str "\"/" % "\"")) first-names))
          (is (not (str/includes? index "\"/js/main.js\""))))
        (let [second-names (release! "2")
              third-names  (release! "3")]
          (testing "the release before stays, for a tab that's still open"
            (is (every? #(.isFile (io/file dir %)) second-names)))
          (testing "but an older one goes"
            (is (not-any? #(.isFile (io/file dir %)) (remove (set third-names) first-names))))))
      (finally
        (run! io/delete-file (reverse (file-seq dir)))))))
