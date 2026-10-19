(ns dk.simongray.podcast-app.memo
  "Remembering the last result of a function whose arguments seldom change
  between two renders of the page, e.g. a view of the library, which
  takes a while to make, while the page renders at every change of the
  state.")

(defn same?
  "Whether the arguments `a` and `b` count as the same: the same
  collection, which is cheap to check, or an equal value of another kind,
  e.g. a string."
  [a b]
  (or (identical? a b)
      (and (not (coll? a)) (= a b))))

(defn remember-last
  "A function that gives what `f` gives for its arguments, and its last
  result again while the `key-fn` of its arguments, a vector, is the same
  as the last time, by same?, e.g. the feeds and the profile of a library.
  Without `key-fn`, the arguments themselves count."
  ([f]
   (remember-last f vector))
  ([f key-fn]
   (let [remembered (atom nil)]
     (fn [& args]
       (let [k             (apply key-fn args)
             [last-k made] @remembered]
         (if (and last-k
                  (= (count k) (count last-k))
                  (every? true? (map same? k last-k)))
           made
           (let [made (apply f args)]
             (reset! remembered [k made])
             made)))))))
