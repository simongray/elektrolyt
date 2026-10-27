(ns dk.simongray.elektrolyt.server.release
  "A release's scripts and stylesheets under names of their content, e.g.
  js/main.3f9a0c1d2e4b5a69.js, which the server lets a browser keep for a
  year without asking, so that a reload never pairs a new page with an old
  file.

  Each file is named once the files that it refers to are, with their new
  names written into it: worker.js's importScripts, app.css's imports,
  main.js's URL of the worker and index.html's links and scripts. The
  files keep the names that shadow-cljs gives them too, and the files of
  the release before stay, so that a tab that's still open loads its own."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]
           [java.security MessageDigest]
           [java.util HexFormat]))

(def content-name
  "The pattern of a file name with the start of the SHA-256 of the file's
  content before its extension."
  #"\.[0-9a-f]{16}\.(?:css|js)$")

(defn content-named?
  "Whether the `path` names a file by its content."
  [path]
  (boolean (re-find content-name path)))

(defn named-by-content
  "The `path` of a script or stylesheet with the start of the SHA-256 of
  its `text` before its extension, e.g. js/main.3f9a0c1d2e4b5a69.js."
  [path ^String text]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes text "UTF-8"))]
    (str/replace path #"\.(css|js)$" (str "." (subs (.formatHex (HexFormat/of) digest) 0 16) ".$1"))))

(defn references
  "The quoted strings by which a file at `from` refers to the file at
  `path`, both relative to the app's folder: its absolute path, and its
  name alone when the two share a folder."
  [from path]
  (let [folder #(str/replace % #"[^/]*$" "")]
    (cond-> [(str "\"/" path "\"")]
      (= (folder from) (folder path)) (conj (str "\"" (subs path (count (folder path))) "\"")))))

(defn refers?
  "Whether the `text` of the file at `from` refers to the file at `path`."
  [text from path]
  (boolean (some #(str/includes? text %) (references from path))))

(defn renamed-in
  "The `text` of the file at `from` with the references to the files of
  `names`, a map of each path to its new one, in their new names."
  [text from names]
  (reduce-kv (fn [text path new]
               (reduce (fn [text [old new]] (str/replace text old new))
                       text
                       (map vector (references from path) (references from new))))
             text
             names))

(defn content-names
  "The new path and text of each of the `files`, a map of path to text, as
  a map of path to the two: named by its content once its references to
  the others name them by theirs."
  [files]
  (loop [named {}
         left  files]
    (if (empty? left)
      named
      (let [refers-to-left? (fn [[from text]]
                              (some #(and (not= from %) (refers? text from %)) (keys left)))
            ready           (remove refers-to-left? left)
            names           (update-vals named first)]
        (when (empty? ready)
          (throw (ex-info "The files refer to each other" {:files (keys left)})))
        (recur (into named (for [[path text] ready
                                 :let        [text (renamed-in text path names)]]
                             [path [(named-by-content path text) text]]))
               (apply dissoc left (map first ready)))))))

(def kept-file
  "The file in a release's folder that lists the files that it names by
  their content, which the next release keeps. Its name starts with a dot,
  so the server never serves it."
  ".content-names.edn")

(defn name-by-content!
  "Copy each script and stylesheet in the release's folder `dir` to a name
  of its content, write index.html with the new names, and delete the
  files named by their content but by this release and the one before."
  [dir]
  (let [path-of #(str (.relativize (.toPath (io/file dir)) (.toPath ^File %)))
        files   (into {} (for [^File f (file-seq (io/file dir))
                               :let  [path (path-of f)]
                               :when (and (.isFile f)
                                          (re-find #"\.(css|js)$" path)
                                          (not (content-named? path)))]
                           [path (slurp f)]))
        named   (content-names files)
        index   (io/file dir "index.html")
        kept    (io/file dir kept-file)
        before  (if (.isFile kept) (edn/read-string (slurp kept)) #{})
        now     (into #{} (map first) (vals named))]
    (doseq [[path text] (vals named)]
      (spit (io/file dir path) text))
    (spit index (renamed-in (slurp index) "index.html" (update-vals named first)))
    (doseq [^File f (file-seq (io/file dir))
            :let  [path (path-of f)]
            :when (and (.isFile f) (content-named? path) (not (now path)) (not (before path)))]
      (io/delete-file f))
    (spit kept (pr-str now))
    now))
