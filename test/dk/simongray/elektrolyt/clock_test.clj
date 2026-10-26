(ns dk.simongray.elektrolyt.clock-test
  "The namespaces of the app that read the player's clock: the player's
  own, and the colours of the page's shell. The main content is made again
  only when the player's content-facts change (ui/main-content), so a view
  of it that read the clock would show it stale. The check reads the
  sources, without their strings and comments."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.runtime-test :refer [code]]))

(def clock-pattern
  "What reads the player's clock: the media state that it shows, the
  position, and the time that moves another tab's position on."
  #"shown-media|current-time|:now(?![\w-])")

(def readers
  "The sources below src/dk/simongray/elektrolyt that may read the clock."
  #{"player.cljc" "player/audio.cljs" "player/ui.cljc" "ui/colors.cljc"})

(deftest the-check-reads-code
  (is (re-find clock-pattern (code "(:current-time (player/shown-media p))")))
  (is (re-find clock-pattern (code "(get player :now)")))
  (is (nil? (re-find clock-pattern (code "(:now-fn opts) \"current-time\" ; :now")))
      "but not another key, a string or a comment"))

(deftest only-the-player-reads-the-clock
  (let [root (io/file "src/dk/simongray/elektrolyt")]
    (doseq [file  (file-seq root)
            :when (re-find #"\.clj[cs]?$" (.getName ^java.io.File file))
            :let  [path (str (.relativize (.toPath root) (.toPath ^java.io.File file)))
                   used (re-find clock-pattern (code (slurp file)))]
            :when used]
      (is (contains? readers path)
          (str file " reads the player's clock with " used
               ", which the main content isn't made again for")))))
