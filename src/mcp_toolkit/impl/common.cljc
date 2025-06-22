(ns mcp-toolkit.impl.common)

(defn user-callback [callback-key]
  (fn [context]
    (when-some [callback (-> context :session deref (get callback-key))]
      (callback context))))
