(ns dk.simongray.elektrolyt.ui.memo
  "Remembering the last result of a slow function whose arguments seldom
  change between renders, e.g. a view of the library.")

(defn same?
  "Whether the arguments `a` and `b` count as the same: identical
  collections, which are cheap to check, or equal values of another kind."
  [a b]
  (or (identical? a b)
      (and (not (coll? a)) (= a b))))

(defn remember-last
  "A version of `f` that returns its last result again while `key-fn`
  returns the same vector for the arguments as last time, compared value
  by value. Without `key-fn`, the arguments themselves count."
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
