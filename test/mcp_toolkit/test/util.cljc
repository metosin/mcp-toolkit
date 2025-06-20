(ns mcp-toolkit.test.util
  (:require [clojure.test :refer [deftest testing is are]]
            [promesa.core :as p]
            [promesa.exec.csp :as sp]))

(defn- assertion-error [message]
  #?(:clj (new AssertionError (str "Assert failed: " message))
     :cljs (js/Error. (str "Assert failed: " message))))

(defn assert-atom
  "Returns a promise which will resolve to true if (pass? @atom) returns true,
   or will reject to an assertion error if not resolved before it times out."
  [atom pass? timeout-ms message]
  (let [watcher-key (gensym "assert-atom")]
    (-> (p/create (fn [resolve reject]
                    (let [check-atom (fn [_watcher-key _atom _old-val new-val]
                                       (when (pass? new-val)
                                         (is true message)
                                         (resolve true)))]
                      (add-watch atom watcher-key check-atom)
                      (check-atom watcher-key atom nil @atom))))
        (p/timeout timeout-ms (assertion-error message))
        (p/handle (fn [val error]
                    (remove-watch atom watcher-key)
                    (or error val))))))
