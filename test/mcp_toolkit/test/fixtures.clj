(ns mcp-toolkit.test.fixtures
  "Test fixtures for mcp-toolkit.
   Provides reusable setup/teardown for registry, session store, and HTTP server tests."
  (:require
   [mcp-toolkit.registry :as reg]
   [mcp-toolkit.transport.streamable-http :as transport]
   [org.httpkit.server :as http]
   [cheshire.core :as json]))

;; ─── Registry Fixtures ──────────────────────────────────────────────────────

(defn with-registry
  "Fixture that provides a fresh registry to each test.
   Test receives a map {:registry reg}."
  [f]
  (f {:registry (reg/create)}))

;; ─── Session Store Fixtures ─────────────────────────────────────────────────

(defn with-session-store
  "Fixture that provides a fresh session store to each test.
   Test receives a map {:store store}."
  [f]
  (f {:store (transport/create-session-store)}))

;; ─── HTTP Test Helpers ──────────────────────────────────────────────────────

(defn mock-request
  "Create a mock HTTP request map for testing handlers.

   method: :get, :post, :delete, etc.
   uri: request URI
   headers: map of header name → value
   body: string or map (maps are JSON-encoded)"
  ([method uri]
   (mock-request method uri {} nil))
  ([method uri headers]
   (mock-request method uri headers nil))
  ([method uri headers body]
   (let [body-bytes (cond
                      (nil? body) nil
                      (string? body) (.getBytes ^String body "UTF-8")
                      (map? body) (.getBytes (json/generate-string body) "UTF-8")
                      :else (.getBytes (str body) "UTF-8"))
         body-stream (when body-bytes
                       (java.io.ByteArrayInputStream. body-bytes))]
     {:request-method method
      :uri uri
      :headers (or headers {})
      :body body-stream})))

(defn handler-response
  "Call an HTTP handler with a mock request and return the response map.
   If the response has a :body string, parses it as JSON."
  [handler method uri & [{:keys [headers body]}]]
  (let [req (mock-request method uri headers body)
        resp (handler req)]
    (if (and (:body resp) (string? (:body resp)))
      (assoc resp :parsed-body
             (try
               (json/parse-string (:body resp) true)
               (catch Exception _
                 (:body resp))))
      resp)))

(defn post-json
  "Convenience: POST JSON to a handler."
  [handler uri body & [{:keys [headers]}]]
  (handler-response handler :post uri
                    {:headers (merge {"Content-Type" "application/json"
                                      "Accept" "application/json, text/event-stream"}
                                     headers)
                     :body body}))

(defn get-request
  "Convenience: GET from a handler. Headers passed as flat map."
  ([handler uri]
   (handler-response handler :get uri {}))
  ([handler uri headers]
   (handler-response handler :get uri {:headers headers})))

(defn delete-request
  "Convenience: DELETE from a handler. Headers passed as flat map."
  ([handler uri]
   (handler-response handler :delete uri {}))
  ([handler uri headers]
   (handler-response handler :delete uri {:headers headers})))

;; (duplicate removed - see get-request for the new multi-arity versions)

;; ─── HTTP Server Fixture ────────────────────────────────────────────────────

(defn with-http-server
  "Fixture that starts a real HTTP server on a random port, runs the test,
   then stops the server.

   Test receives a map {:port port :handler handler :dispatch-fn dispatch-fn}.

   The dispatch-fn is a simple echo dispatch for testing:
   - initialize → returns server info
   - tools/list → returns empty tools
   - tools/call → returns echo of tool name + args
   - anything else → nil (notification)"
  [f]
  (let [dispatch-fn (fn [message _session-id]
                      (case (:method message)
                        "initialize" {:jsonrpc "2.0"
                                      :id (:id message)
                                      :result {:protocolVersion "2025-11-25"
                                               :capabilities {:tools {:listChanged true}}
                                               :serverInfo {:name "test-server" :version "0.1.0"}}}
                        "tools/list" {:jsonrpc "2.0"
                                      :id (:id message)
                                      :result {:tools []}}
                        "tools/call" {:jsonrpc "2.0"
                                      :id (:id message)
                                      :result {:content [{:type "text"
                                                          :text (str "Called "
                                                                     (get-in message [:params :name])
                                                                     " with "
                                                                     (get-in message [:params :arguments]))}]}}
                        nil))
        session-store (transport/create-session-store)
        handler (transport/create-handler dispatch-fn {:session-store session-store})
        server (http/run-server handler {:port 0 :ip "127.0.0.1"})
        port (:local-port (meta server))]
    (try
      (f {:port port
          :handler handler
          :dispatch-fn dispatch-fn
          :session-store session-store
          :server server})
      (finally
        (http/server-stop! server)))))

;; ─── Test Data Generators ───────────────────────────────────────────────────

(defn make-tool
  "Create a valid tool map for testing."
  [& {:keys [name description input-schema handler timeout dependencies group]
      :or {name :test/tool
           description "A test tool"
           input-schema {:type "object" :properties {}}
           handler (fn [_ args] {:content [{:type "text" :text (str "ok: " args)}]})
           timeout nil
           dependencies nil
           group nil}}]
  {:name name
   :description description
   :inputSchema input-schema
   :handler handler
   :timeout timeout
   :dependencies dependencies
   :group group})

(defn make-plugin
  "Create a valid plugin map for testing."
  [& {:keys [name version tools prompts resources dependencies config lifecycle]
      :or {name :test-plugin
           version "0.1.0"
           tools [(make-tool)]
           prompts nil
           resources nil
           dependencies nil
           config nil
           lifecycle nil}}]
  (cond-> {:name name
           :version version
           :tools tools}
    prompts (assoc :prompts prompts)
    resources (assoc :resources resources)
    dependencies (assoc :dependencies dependencies)
    config (assoc :config config)
    lifecycle (assoc :lifecycle lifecycle)))
