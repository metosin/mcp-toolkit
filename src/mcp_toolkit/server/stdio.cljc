(ns mcp-toolkit.server.stdio
  (:require #?(:clj [jsonista.core :as j])
            [mcp-toolkit.server.json-rpc-message :as json-rpc])
  #?(:clj
     (:import (clojure.lang LineNumberingPushbackReader)
              (java.io OutputStreamWriter))))

;;
;; STDIO transport
;;

#?(:clj
   (defn create-stdio-context [session
                               ^LineNumberingPushbackReader reader
                               ^OutputStreamWriter writer]
     (let [json-mapper (j/object-mapper {:encode-key-fn name
                                         :decode-key-fn keyword})
           send-message (fn [message]
                          (.write writer (j/write-value-as-string message json-mapper))
                          (.write writer "\n")
                          (.flush writer))
           read-message (fn []
                          (loop []
                            ;; line = nil means that the reader is closed
                            (when-some [line (.readLine reader)]
                              (let [message (try
                                              (j/read-value line json-mapper)
                                              (catch Exception e
                                                (send-message json-rpc/parse-error-response)
                                                nil))]
                                (if (nil? message)
                                  (recur)
                                  message)))))]
       {:session session
        :send-message send-message
        :read-message read-message})))
