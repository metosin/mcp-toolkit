(ns example.my-server
  (:require [clojure.string :as str]
            [mcp-toolkit.server :as server]
            [mcp-toolkit.json-rpc :as json-rpc]
            [promesa.core :as p]
            [example.server-content :as content]
            #?(:clj [jsonista.core :as j])
            #?(:clj [nrepl.server :as nrepl]))
  #?(:clj (:import (clojure.lang LineNumberingPushbackReader)
                   (java.io OutputStreamWriter))))

;; Example of usage of this library.

;;
;; State
;;

(def session
  (atom
    (server/create-session {:prompts [content/talk-like-pirate-prompt]
                            :resources [content/hello-doc-resource
                                        content/world-doc-resource]
                            :tools [content/parentify-tool]
                            :resource-templates content/my-resource-templates
                            :resource-uri-complete-fn content/my-resource-uri-complete-fn})))

;;
;; Platform-specific threading, transport & I/O stuffs
;;

;; on the JVM

#?(:clj
   (def context
     {:session session
      :send-message (let [^OutputStreamWriter writer *out*
                          json-mapper (j/object-mapper {:encode-key-fn name})]
                      (fn [message]
                        (.write writer (j/write-value-as-string message json-mapper))
                        (.write writer "\n")
                        (.flush writer)))}))

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
                 (json-rpc/handle-message context message)
                 (recur)))))))))

#?(:clj
   (defn main [{:keys [bind port]}]
     ;; In this example, we launch an nREPL server which can be used to hack the MCP server while it is running.
     ;; You might not need it, in which case feel free to remove it.
     (let [server (nrepl/start-server {:bind bind
                                       :port port})]
       (try
         ;; listen-messages returns once *in* is closed.
         (listen-messages context *in*)
         (finally
           (nrepl/stop-server server))))))

;; on Node JS

#?(:cljs
   (def context
     {:session session
      :send-message (fn [message]
                      (js/process.stdout.write (-> message
                                                   clj->js
                                                   js/JSON.stringify
                                                   (str "\n"))))}))

#?(:cljs
   (defn main [& args]
     (js/process.stdin.setEncoding "utf8")
     (js/process.stdout.setEncoding "utf8")

     (js/process.stdin.on "data"
                          (fn [chunk]
                            ;; In this simple example, we naively assume that there is a json object per line.
                            (doseq [line (str/split-lines chunk)]
                              (when-some [message (try
                                                    (-> line
                                                        js/JSON.parse
                                                        (js->clj :keywordize-keys true))
                                                    (catch js/SyntaxError e
                                                      (json-rpc/send-message context json-rpc/parse-error-response)
                                                      (js/process.stderr.write (str "<<-" line "->>"))
                                                      nil))]
                                (json-rpc/handle-message context message)))))
     (js/process.stdin.on "end"
                          (fn []
                            (js/process.exit 0)))))

;;
;; Things to run in the REPL while the server is running
;;

(comment
  ;; tail -n 20 -F ~/Library/Logs/Claude/mcp-server-toolkit.log

  @session
  (:message-log @session)
  (swap! session update :message-log empty)

  (server/add-prompt context talk-like-pirate-prompt)
  (server/remove-prompt context talk-like-pirate-prompt)

  (server/add-resource context hello-world-resource)
  (server/remove-resource context hello-world-resource)

  ;; Simulates changing a resource.
  (swap! session update-in [:resource-by-uri "file:///doc/hello.md" :text] str " xxx")
  (server/notify-resource-updated context {:uri "file:///doc/hello.md"})

  (server/set-resource-templates context my-resource-templates)
  (server/set-resource-uri-complete-fn context my-resource-uri-complete-fn)

  (server/add-tool context parentify-tool)
  (server/remove-tool context parentify-tool)

  (server/send-log-data context "info" "mcp-toolkit" {:message "Made in Finland"})
  (server/send-log-data context "emergency" "datacenter" {:error "HCF"})

  (some-> (server/request-sampling context {:messages [{:role "user"
                                                        :content {:type "text"
                                                                  :text "What is the capital of France?"}}]
                                            :modelPreferences {:hints [{:name "claude-3-sonnet"}]
                                                               :intelligencePriority 0.8
                                                               :speedPriority 0.5}
                                            :systemPrompt "You are a helpful assistant"
                                            :maxTokens 100})
          deref)

  ,)
