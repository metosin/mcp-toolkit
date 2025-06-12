(ns example.my-server
  (:require [clojure.string :as str]
            [mcp-toolkit.server.core :as server]
            [mcp-toolkit.server.json-rpc-message :as json-rpc]
            [promesa.core :as p]
            #?(:clj [jsonista.core :as j])
            #?(:clj [nrepl.server :as nrepl]))
  #?(:clj (:import (clojure.lang LineNumberingPushbackReader)
                   (java.io OutputStreamWriter))))

;; Example of usage of this library.

(def talk-like-pirate-prompt
  {:name "pirate_mode_prompt"
   :description "Talk like a pirate prompt"
   :arguments [{:name "expressions"
                :description "Comma-separated expressions"
                :required false}]
   :complete-fn (fn [context name value]
                  (when (= name "expressions")
                    (when-not (str/includes? name "!")
                      {:completion {:values [(str value "!")
                                             (str value "~#!")
                                             (str value "!#@!!")]
                                    :total 3
                                    :hasMore false}})))
   :prompt-fn (fn [context {:keys [expressions]}]
                {:description (str "A talk-like-a-pirate prompt which includes the expressions: " expressions)
                 :messages [{:role "user"
                             :content {:type "text"
                                       :text (->> ["You are a sea pirate and you need to talk to the user in a tone that reassembles talk-like-a-pirate style."
                                                   (when-not (str/blank? expressions)
                                                     (str "You also need to randomly use the following expressions every few sentences: " expressions "."))
                                                   (str "Start by introducing yourself in a spectacular way and talk about your pet parrot sitting on your shoulder. "
                                                        "Ask the user to choose a new and creative name for the parrot.")]
                                                  (filter some?)
                                                  (str/join " "))}}]})})

(def hello-world-resource
  {:uri "file:///doc/hello.md"
   :name "hello.md"
   :description "Documentation's intro"
   :mimeType "text/markdown; charset=UTF-8"
   ;;:blob ,,,
   :text "Hello, `world!`"})

(def my-resource-templates
  [{:uriTemplate "file:///my-root-dir/{path}"
    :name "Project files"
    :description "Access files in the project directory"
    :mimeType "application/octet-stream"}])

(defn my-resource-uri-complete-fn [context uri name value]
  (when (and (= uri "file:///my-root-dir/{path}")
             (= name "path"))
    (let [paths ["about" "alpha" "beta"]
          values (filterv (fn [path] (str/starts-with? path value)) paths)]
      {:completion {:values (take 100 values)
                    :total (count values)
                    :hasMore (> (count values) 100)}})))

(def parentify-tool
  {:name "parentify"
   :description "Parentify a text: wraps a text within parenthesis."
   :inputSchema {:type "object"
                 :properties {:text {:type "string"
                                     :description "the text to be parentified"}}
                 :required [:text]}
   :tool-fn (fn [context arguments]
              (-> (p/let [text (str "(" (:text arguments) ")")
                          _ (p/delay 1000)
                          _ (server/notify-progress context {:progress 1
                                                             :total 3
                                                             :message "thinking ..."})
                          _ (p/delay 1000)
                          _ (server/notify-progress context {:progress 2
                                                             :total 3
                                                             :message "thinking harder ..."})
                          _ (when @(:is-cancelled context)
                              (throw (ex-info "tool was cancelled" {:note "too bad, was almost done"})))

                          _ (p/delay 1000)]
                    {:content [{:type "text"
                                :text text}]
                     :isError false})
                  (p/catch (fn [exception]
                             {:content [{:type "text"
                                         :text (str "Something went wrong: " (ex-message exception))}]
                              :isError true}))))})

;;
;; State
;;

(def session
  (server/create-session {:prompts [talk-like-pirate-prompt]
                          :resources [hello-world-resource]
                          :tools [parentify-tool]
                          :resource-templates my-resource-templates
                          :resource-uri-complete-fn my-resource-uri-complete-fn
                          :on-client-roots-updated (fn [context] ,,,)}))

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
                 (server/handle-message (-> context
                                            (assoc :message message)))
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
                                                      (js/process.stderr.write (str "<<-" line "->>"))
                                                      nil))]
                                (server/handle-message (assoc context :message message))))))
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

  @(server/request-sampling context {:messages [{:role "user"
                                                 :content {:type "text"
                                                           :text "What is the capital of France?"}}]
                                     :modelPreferences {:hints [{:name "claude-3-sonnet"}]
                                                        :intelligencePriority 0.8
                                                        :speedPriority 0.5}
                                     :systemPrompt "You are a helpful assistant"
                                     :maxTokens 100})

  ,)
