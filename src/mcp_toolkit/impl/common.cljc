(ns ^:no-doc mcp-toolkit.impl.common
  (:require [clojure.string :as str]))

(defn user-callback [callback-key]
  (fn [context]
    (when-some [callback (-> context :session deref (get callback-key))]
      (callback context))))

(defn munge-name
  "Convert a namespaced keyword to an MCP protocol string.
   :art19/list-episodes → \"art19__list_episodes\"

   Non-namespaced keywords are returned as-is (string form)."
  [k]
  (if (namespace k)
    (str (namespace k) "__" (name k))
    (name k)))

(defn unmunge-name
  "Convert an MCP protocol string to a namespaced keyword.
   \"art19__list_episodes\" → :art19/list-episodes

   Strings without '__' become non-namespaced keywords."
  [s]
  (if (str/includes? s "__")
    (let [[ns name] (str/split s #"__" 2)]
      (keyword ns name))
    (keyword s)))
