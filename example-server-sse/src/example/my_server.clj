(ns example.my-server
  (:require
   [clojure.string :as str]
   [example.transport.sse :as sse]
   [mcp-toolkit.server :as server]
   [org.httpkit.server :as http-kit]
   [promesa.core :as p]
   [reitit.ring :as reitit]
   [taoensso.telemere :as tel])
  (:import
   (java.util.concurrent Executors)))

;; Make futures use virtual threads
(set-agent-send-executor!
 (Executors/newVirtualThreadPerTaskExecutor))

(set-agent-send-off-executor!
 (Executors/newVirtualThreadPerTaskExecutor))

;; Example of usage of this library.

(def talk-like-pirate-prompt
  {:name        "pirate_mode_prompt"
   :description "Talk like a pirate prompt"
   :arguments   [{:name        "expressions"
                  :description "Comma-separated expressions"
                  :required    false}]
   :complete-fn (fn [context name value]
                  (when (= name "expressions")
                    (when-not (str/includes? name "!")
                      {:completion {:values  [(str value "!")
                                              (str value "~#!")
                                              (str value "!#@!!")]
                                    :total   3
                                    :hasMore false}})))
   :prompt-fn   (fn [context {:keys [expressions]}]
                  {:description (str "A talk-like-a-pirate prompt which includes the expressions: " expressions)
                   :messages    [{:role    "user"
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
  {:name        "parentify"
   :description "Parentify a text: wraps a text within parenthesis."
   :inputSchema {:type       "object"
                 :properties {:text {:type        "string"
                                     :description "the text to be parentified"}}
                 :required   [:text]}
   :tool-fn     (fn [context arguments]
                  (-> (p/let [text (str "(" (:text arguments) ")")
                              _ (p/delay 1000)
                              _ (server/notify-progress context {:progress 1
                                                                 :total    3
                                                                 :message  "thinking ..."})
                              _ (p/delay 1000)
                              _ (server/notify-progress context {:progress 2
                                                                 :total    3
                                                                 :message  "thinking harder ..."})
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

(defn create-session
  "Creates an mcp-toolkit server session atom.

  Will be used to create a new session for each client connection.

  Accepts the http server context and a session id.
  "
  [_context _session-id]
  (atom
   (server/create-session {:prompts                  [talk-like-pirate-prompt]
                           :resources                [hello-world-resource]
                           :tools                    [parentify-tool]
                           :resource-templates       my-resource-templates
                           :resource-uri-complete-fn my-resource-uri-complete-fn})))

(def context
  "This the the http server context, not the mcp-toolkit context."
  {:dev?              true
   :create-session-fn create-session
   :settings          {:allowed-hosts ["127.0.0.1:*"]}})

(defn log-request [{:keys [uri request-method] :as req} {:keys [body status] :as resp}]
  (tel/log! {:level :info :msg (str (str/upper-case (name request-method)) " " uri)
             :data  (merge  {:status status}
                            (when (>= (or status -1) 400)
                              {:err          body
                               :resp-headers (select-keys  resp [:headers])
                               :req-headers  (select-keys  req [:headers])}))}))

(defn log-request-middleware [handler]
  (fn [req]
    (let [resp (handler req)]
      (log-request req resp)
      resp)))

(defn routes [ctx]
  ["" {:middleware [log-request-middleware]}
   (sse/routes ctx)])

(defn handler [ctx]
  (let [dev-mode (:dev? ctx)
        f        (fn [] (reitit/ring-handler (reitit/router (routes ctx))))]
    (if dev-mode
      (reitit/reloading-ring-handler f)
      (f))))

(defn start-http
  [ctx {:keys [bind port]}]
  (println (str "Starting HTTP server on " bind ":" port))
  (assoc ctx ::server
         (http-kit/run-server (handler ctx)
                              {:legacy-return-value? false
                               :port                 port
                               :ip                   bind})))

(defn stop-http [{::keys [server]}]
  (when server
    (when-let [result (http-kit/server-stop! server {:timeout 1000})]
      @result)))

;; Logging control

(tel/set-min-level! :info)

;; (tel/add-handler! ::file (tel/handler:file))
;; (tel/remove-handler! ::file )

;; in a real application you would probably use something like integrant, component, mount, etc.
(defn ctx-start [opts]
  (-> context
      (sse/ctx-start)
      (start-http opts)))

(defn ctx-stop [ctx]
  (-> ctx
      (stop-http)))

(defonce _ctx (atom nil))

(defn restart []
  (ctx-stop @_ctx)
  (reset! _ctx (ctx-start {:host "127.0.0.1" :port 3000})))

(defn main [opts]
  (ctx-start opts))

(comment
  (restart) ;; rcf
  (clojure.repl.deps/sync-deps)
  ;;
  )
