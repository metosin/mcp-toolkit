(ns example.my-server
  (:require
   [clojure.string :as str]
   [example.server-content :as content]
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

(defn create-session
  "Creates a mcp-toolkit server session atom.

  Will be used to create a new session for each client connection.

  Accepts the http server context and a session id.
  "
  [_context _session-id]
  (atom
   (server/create-session {:prompts                  [content/talk-like-pirate-prompt]
                           :resources                [content/hello-doc-resource
                                                      content/world-doc-resource]
                           :tools                    [content/parentify-tool]
                           :resource-templates       content/my-resource-templates
                           :resource-uri-complete-fn content/my-resource-uri-complete-fn})))

(def default-transport-env
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

(defonce system (atom nil))

(defn start [opts]
  (-> default-transport-env
      (sse/ctx-start)
      (start-http opts)))

(defn stop []
  (-> @system
      (stop-http)))

(defn restart [opts]
  (stop)
  (reset! system (start opts)))

(defn main [opts]
  (start opts))

(comment
  ;; From a repl you can run the system  with
  (start {:host "127.0.0.1" :port 3000})
  ;; and stop it with
  (stop)
  ;; or do both at once
  (restart {:host "127.0.0.1" :port 3000})
  ;;
  ,)
