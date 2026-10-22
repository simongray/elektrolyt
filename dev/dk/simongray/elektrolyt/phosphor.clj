(ns dk.simongray.elektrolyt.phosphor
  "Write the app's icons, ui/icons.cljc, from Phosphor's npm package.

  The package, @phosphor-icons/core, has each icon in each weight as an svg
  of one path, filled with currentColor on a grid of 256 by 256. The app's
  namespace has a def for the path of each icon of one weight, named as
  Phosphor names it, e.g. play or gear-six. A release build keeps only the
  defs that the views use, where a map of all 1,512 icons would keep them
  all."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]))

(def package
  "The folder of Phosphor's npm package, whose version package.json pins."
  "node_modules/@phosphor-icons/core")

(def target
  "The file of the app's namespace of icons."
  "src/dk/simongray/elektrolyt/ui/icons.cljc")

(defn version
  "The version of Phosphor in the npm package."
  []
  (second (re-find #"\"version\": \"([^\"]+)\""
                   (slurp (io/file package "package.json")))))

(defn icon-name
  "The name of the icon in the `file` of the `weight`, e.g. play for
  play-light.svg, since every weight but Regular ends its files' names in
  its own."
  [weight file]
  (str/replace (.getName (io/file file))
               (re-pattern (str "(-" weight ")?\\.svg$"))
               ""))

;; The app's icon alias draws the path on this grid, so a file with
;; anything else in it, or another grid, stops the run
(defn path
  "The path of an icon from the text of its `svg` file, which holds one
  path on a grid of 256 by 256 and nothing else."
  [svg]
  (or (second (re-matches #"(?s)<svg [^>]*viewBox=\"0 0 256 256\"[^>]*><path d=\"([^\"]+)\"/></svg>\s*"
                          svg))
      (throw (ex-info "Not an svg of one path on a grid of 256" {:svg svg}))))

(defn icons
  "The path of each icon of the `weight`, e.g. \"light\", by its name."
  [weight]
  (into (sorted-map)
        (for [file (.listFiles (io/file package "assets" weight))]
          [(icon-name weight file) (path (slurp file))])))

(defn defined-name
  "The name that the top-level `form` defines, e.g. \"list\" for (defn list
  ...), or nil."
  [form]
  (when (and (seq? form)
             (symbol? (first form))
             (str/starts-with? (name (first form)) "def")
             (symbol? (second form)))
    (name (second form))))

;; Only the names matter, so a tagged literal reads as its form, and a
;; keyword of an alias, e.g. ::ana/numeric, as the alias's own
(defn defined-names
  "The names that the source file `resource` on the classpath defines at
  its top level, reading the parts of the JVM's reader conditionals."
  [resource]
  (with-open [r (reader-types/indexing-push-back-reader
                 (io/reader (io/resource resource)))]
    (binding [reader/*default-data-reader-fn* (fn [_ form] form)
              reader/*alias-map*              identity]
      (->> (repeatedly #(reader/read {:eof ::eof :read-cond :allow} r))
           (take-while #(not= ::eof %))
           (keep defined-name)
           set))))

(defn cljs-core-names
  "The names of cljs.core's functions and macros, from the ClojureScript
  that shadow-cljs brings."
  []
  (into (defined-names "cljs/core.cljs") (defined-names "cljs/core.cljc")))

;; A def of a name that clojure.core or cljs.core has would replace the
;; core var, with a warning on each build
(defn clashes
  "The `names` that clojure.core or cljs.core has a var of."
  [names]
  (let [core (into (set (map name (keys (ns-publics 'clojure.core))))
                   (cljs-core-names))]
    (filter core names)))

(defn licence
  "Phosphor's licence from the npm package, as lines of comment."
  []
  (->> (str/split-lines (slurp (io/file package "LICENSE")))
       (map #(str/trimr (str ";; " %)))
       (str/join "\n")))

(defn source
  "The source of the namespace of the `icons` of the `weight`, with the
  names in `excluded` excluded from clojure.core."
  [weight icons excluded]
  (str ";; Phosphor Icons " (version) ", the " (str/capitalize weight)
       " weight, from the npm package\n"
       ";; @phosphor-icons/core. dk.simongray.elektrolyt.phosphor writes this\n"
       ";; file, so change that rather than this.\n"
       ";;\n"
       (licence) "\n"
       "(ns dk.simongray.elektrolyt.ui.icons\n"
       "  \"Phosphor's icons in the " (str/capitalize weight)
       " weight: a def for the path of each,\n"
       "  named as Phosphor names it, e.g. play or gear-six.\n"
       "\n"
       "  The icon alias of ui.controls draws a path in the colour of the text\n"
       "  around it. A release build keeps only the icons that the views use.\n"
       "  Phosphor's site shows them all: https://phosphoricons.com\"\n"
       "  (:refer-clojure :exclude [" (str/join " " excluded) "]))\n"
       "\n"
       (str/join (for [[k d] icons]
                   (str "(def " k " " (pr-str d) ")\n")))))

(defn write-icons!
  "Write the icons of the `weight`, e.g. \"light\", to the app's namespace
  of icons, and return how many there are."
  [weight]
  (let [icons (icons weight)]
    (spit target (source weight icons (clashes (keys icons))))
    (count icons)))

(comment
  ;; the weight that Simon chose over Regular, from a scene of both
  (write-icons! "light")
  ;; => 1512
  (clashes (keys (icons "light")))
  #_.)
