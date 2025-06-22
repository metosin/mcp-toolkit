(ns example.my-client
  (:require [clojure.string :as str]
            [mcp-toolkit.client :as client]
            [mcp-toolkit.json-rpc :as json-rpc]
            [promesa.core :as p]
            #?(:clj [jsonista.core :as j])
            #?(:cljs ["child_process" :refer [spawn]])
            #?(:cljs ["path" :as path]))
  #?(:clj (:import (clojure.lang LineNumberingPushbackReader)
                   (java.io BufferedReader
                            BufferedWriter
                            File
                            InputStreamReader
                            OutputStreamWriter))))

;; Example of usage of this library.

(def roots [[{:uri "file:///home/user/projects/my-root"
              :name "My project root"}]])

(def context (atom nil))

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
           (when-some [message (try
                                 ;; In this simple example, we naively assume that there is a json object per line.
                                 (-> (j/read-value line json-mapper))
                                 (catch Exception e
                                   (send-message json-rpc/parse-error-response)
                                   nil))]
             (prn [:<-- message])
             (json-rpc/handle-message (-> context
                                          (assoc :message message))))
           (recur))))))

#?(:clj
   (defn -main [& args]
     (let [;; Start a server process
           ^Process server-process (-> (ProcessBuilder. ["clojure" "-X:mcp-server"])
                                       (.directory (File. "../example-server"))
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
                     (client/create-session {:roots roots}))

           ctx {:session session
                :send-message (let [json-mapper (j/object-mapper {:encode-key-fn name})]
                                (fn [message]
                                  (prn [:--> message])
                                  (.write writer (j/write-value-as-string message json-mapper))
                                  (.write writer "\n")
                                  (.flush writer)))
                :close-connection (fn []
                                    (.close reader)
                                    (.close writer))}]
       ;; Make the context available as a top level var, for REPL use.
       (reset! context ctx)

       ;; Listen on the reader in a separate thread.
       (future (listen-messages ctx reader))

       ;; Initiate the handshake
       (client/send-first-handshake-message ctx))))

;; on Node JS

#?(:cljs
   (defn main [& args]
     (let [;; Start a server process
           server-process (spawn "clojure" #js ["-X:mcp-server"]
                                #js {:cwd (.resolve path ".." "example-server")
                                     :stdio #js ["pipe"    ; writable stdin
                                                 "pipe"    ; writable stdout
                                                 "inherit" ; stderr
                                                 ,]})
           ;; A writer to write on the server's stdin
           writer (.-stdin server-process)
           ;; A reader to read lines from the server's stdout
           reader (.-stdout server-process)
           
           session (atom
                    (client/create-session {:roots roots}))
           
           ctx {:session session
                :send-message (fn [message]
                                (prn [:--> message])
                                (.write writer (str (-> message clj->js js/JSON.stringify) "\n")))
                :close-connection (fn []
                                    (.kill server-process))}]
       
       ;; Make the context available as a top level var, for REPL use.
       (reset! context ctx)
       
       ;; Listen on the reader
       (.on reader "data"
            (fn [chunk]
              ;; In this simple example, we naively assume that there is a json object per line.
              (doseq [line (str/split-lines chunk)]
                (when-some [message (try
                                      (-> line
                                          js/JSON.parse
                                          (js->clj :keywordize-keys true))
                                      (catch js/SyntaxError e
                                        (json-rpc/send-message ctx json-rpc/parse-error-response)
                                        (js/process.stderr.write (str "<<-" line "->>"))
                                        nil))]
                  (prn [:<-- message])
                  (json-rpc/handle-message (assoc ctx :message message))))))

       ;; Initiate the handshake
       (client/send-first-handshake-message ctx))))

;;
;; Things to run in the REPL while the server is running
;;

(comment

  (-main)

  (-> @context :session deref)

  (json-rpc/close-connection @context)

  ,)
