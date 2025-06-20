(ns example.my-client
  (:require [mcp-toolkit.client :as client]
            [mcp-toolkit.json-rpc :as json-rpc]
            [promesa.core :as p]
            #?(:clj [jsonista.core :as j]))
  #?(:clj (:import (clojure.lang LineNumberingPushbackReader)
                   (java.io BufferedReader
                            BufferedWriter
                            File
                            InputStreamReader
                            OutputStreamWriter))))

;; Example of usage of this library.


;;
;; Platform-specific threading, transport & I/O stuffs
;;

;; on the JVM

#?(:clj
   (defn listen-messages [context
                          ^LineNumberingPushbackReader reader]
     (let [{:keys [send-message]} context
           json-mapper (j/object-mapper {:decode-key-fn keyword})]
       (loop []
         ;; line = nil means that the reader is closed
         (when-some [line (.readLine reader)]
           (let [message (try
                           ;; In this simple example, we naively assume that there is a json object per line.
                           (-> (j/read-value line json-mapper))
                           (catch Exception e
                             (send-message json-rpc/parse-error-response)
                             nil))]
             (if (nil? message)
               (recur)
               (do
                 (prn [:<-- message])
                 (json-rpc/handle-message (-> context
                                              (assoc :message message)))
                 (recur)))))))))

(def context (atom nil))

#?(:clj
   (defn -main [& args]
     (let [^Process server-process (-> (ProcessBuilder. ["clojure" "-X:mcp-server"])
                                       (.directory (File. "./example-server"))
                                       (.start))
           ;; A writer to write on the server's stdin
           writer (-> (.getOutputStream server-process)
                      (OutputStreamWriter.)
                      (BufferedWriter.))
           ;; A reader to read lines from the server's stdout
           reader (-> (.getInputStream server-process)
                      (InputStreamReader.)
                      (BufferedReader.)
                      (LineNumberingPushbackReader.))

           session (atom
                     (client/create-session {:roots                       [{:uri (-> (File. "my-root") (.getAbsolutePath))}]
                                             :on-server-prompts-updated   (fn [context] ,,,)
                                             :on-server-resources-updated (fn [context] ,,,)
                                             :on-server-tools-updated     (fn [context] ,,,)}))

           ctx {:session session
                :send-message (let [json-mapper (j/object-mapper {:encode-key-fn name})]
                                (fn [message]
                                  (prn [:--> message])
                                  (.write writer (j/write-value-as-string message json-mapper))
                                  (.write writer "\n")
                                  (.flush writer)))
                :close-connection (fn []
                                    (.close reader))}

           ;; Makes the context available as a top level var, for REPL users.
           _ (reset! context ctx)

           ,]

       ;; Listen on the reader in a separate thread.
       (future
         (prn "Reader thread started.")

         ;; Let's initiate the handshake
         (client/send-first-handshake-message ctx)

         ;; Blocking loop on the reader
         (listen-messages ctx reader))

       ,)))

;; on Node JS

;;
;; Things to run in the REPL while the server is running
;;

(comment

  (-main)

  (-> @context :session deref)

  (json-rpc.handler/close-connection @context)

  ,)
