(ns mcp-toolkit.transport.streamable-http
  "Streamable HTTP transport for MCP servers.

   Implements the MCP 2025-11-25 Streamable HTTP transport spec:
   https://modelcontextprotocol.io/specification/2025-11-25/basic/transports

   Features:
   - POST /mcp — client→server messages (JSON-RPC requests, notifications, responses)
   - GET /mcp  — server→client SSE stream (notifications, requests)
   - DELETE /mcp — session termination
   - Session management via MCP-Session-Id header
   - Origin validation (configurable, MUST for security)
   - Health check at /health

   Usage:
     (def dispatch-fn (fn [message session-id] ...))
     (def handler (create-handler dispatch-fn {:session-store (create-session-store)}))
     (http/run-server handler {:port 3000})"
  (:require [clojure.string :as str]
            [org.httpkit.server :as http]
            [cheshire.core :as json]))

;; ─── Session Store ──────────────────────────────────────────────────────────

(defn create-session-store
  "Create an empty session store.
   Internal structure: {session-id {:created-at timestamp :last-active timestamp}}"
  []
  (atom {}))

(defn create-session!
  "Create a new session and return its ID."
  ([store]
   (create-session! store nil))
  ([store extra]
   (let [sid (str (java.util.UUID/randomUUID))
         now (System/currentTimeMillis)]
     (swap! store assoc sid (merge {:created-at now :last-active now} extra))
     sid)))

(defn valid-session?
  "Check if a session ID is valid."
  [store sid]
  (boolean (and sid (contains? @store sid))))

(defn touch-session!
  "Update the last-active timestamp for a session."
  [store sid]
  (when sid
    (swap! store update-in [sid :last-active] (constantly (System/currentTimeMillis)))))

(defn delete-session!
  "Delete a session. Returns true if it existed."
  [store sid]
  (if (contains? @store sid)
    (do (swap! store dissoc sid)
        true)
    false))

(defn prune-expired-sessions!
  "Remove sessions older than ttl-ms. Returns count of removed sessions."
  ([store]
   (prune-expired-sessions! store (* 60 60 1000))) ; 1 hour default
  ([store ttl-ms]
   (let [cutoff (- (System/currentTimeMillis) ttl-ms)
         before (count @store)]
     (swap! store (fn [sessions]
                    (reduce-kv (fn [acc k v]
                                 (if (< (:created-at v) cutoff)
                                   acc
                                   (assoc acc k v)))
                               {}
                               sessions)))
     (- before (count @store)))))

;; ─── HTTP Helpers ───────────────────────────────────────────────────────────

(defn- find-header
  "Find an HTTP header by name (case-insensitive).
   http-kit lowercases header names, but we handle both cases."
  [request header-name]
  (let [headers (:headers request)
        low-name (str/lower-case header-name)]
    (or (get headers low-name)
        (get headers (keyword low-name))
        (some (fn [[k v]]
                (when (= low-name (str/lower-case (name k)))
                  v))
              headers))))

(defn- json-response
  "Create a JSON HTTP response."
  ([status body]
   (json-response status body {}))
  ([status body extra-headers]
   {:status status
    :headers (merge {"Content-Type" "application/json"} extra-headers)
    :body (json/generate-string body)}))

(defn- accepted-response
  "Create a 202 Accepted response (for notifications/responses)."
  ([]
   (accepted-response {}))
  ([extra-headers]
   {:status 202
    :headers (merge {"Content-Type" "application/json"} extra-headers)
    :body ""}))

(defn- parse-body
  "Parse the JSON body from an HTTP request."
  [request]
  (let [body-stream (:body request)
        body-str (when body-stream (slurp body-stream))]
    (when (and body-str (not (str/blank? body-str)))
      (try
        (json/parse-string body-str true)
        (catch Exception _
          :parse-error)))))

;; ─── Origin Validation ──────────────────────────────────────────────────────

(defn- validate-origin
  "Validate the Origin header against allowed origins.
   Returns true if origin is allowed or if allowed-origins is nil/empty."
  [request allowed-origins]
  (if (empty? allowed-origins)
    true
    (let [origin (find-header request "Origin")]
      (or (nil? origin)
          (contains? (set allowed-origins) origin)))))

;; ─── Request Handlers ───────────────────────────────────────────────────────

(defn- handle-post
  "Handle POST /mcp — client→server messages.

   For initialize: creates session, returns InitializeResult with MCP-Session-Id header.
   For notifications/responses: returns 202 Accepted.
   For requests: returns JSON response or opens SSE stream."
  [request dispatch-fn session-store allowed-origins]
  (if-not (validate-origin request allowed-origins)
    (json-response 403
                   {:jsonrpc "2.0"
                    :error {:code -32600
                            :message "Forbidden: invalid Origin header"}})
    (let [body (parse-body request)]
      (cond
        (nil? body)
        (json-response 400 {:error "Missing request body"})

        (= body :parse-error)
        (json-response 400 {:error "Invalid JSON"})

        :else
        (let [session-id (find-header request "MCP-Session-Id")
              method (:method body)
              new-session? (= method "initialize")
              sid (if new-session?
                    (create-session! session-store)
                    session-id)]

          (if (and (not new-session?) (not (valid-session? session-store sid)))
            (json-response 400
                           {:error "Invalid or missing MCP-Session-Id"})
            (do
              (touch-session! session-store sid)
              (let [response (dispatch-fn body sid)]
                (if (nil? response)
                  ;; Notification or response — 202 Accepted
                  (accepted-response (when new-session? {"MCP-Session-Id" sid}))
                  ;; Request — return JSON response with session header
                  (json-response 200 response
                                 (when new-session? {"MCP-Session-Id" sid})))))))))))

(defn- handle-get
  "Handle GET /mcp — server→client SSE stream.

   Opens an SSE stream for server-initiated messages.
   Supports Last-Event-ID for resumability."
  [request session-store allowed-origins]
  (if-not (validate-origin request allowed-origins)
    (json-response 403
                   {:jsonrpc "2.0"
                    :error {:code -32600
                            :message "Forbidden: invalid Origin header"}})
    (let [session-id (find-header request "MCP-Session-Id")
          last-event-id (find-header request "Last-Event-ID")]

      (if (and session-id (not (valid-session? session-store session-id)))
        (json-response 404 {:error "Session not found"})
        ;; Open SSE channel
        (http/as-channel request
                         {:on-open (fn [ch]
                      ;; Send priming event for resumability
                                     (when last-event-id
                                       (http/send! ch
                                                   (str "id: " last-event-id "\ndata: \n\n")
                                                   :text)))
                          :on-close (fn [_ch _status]
                       ;; Session cleanup if needed
                                      nil)})))))

(defn- handle-delete
  "Handle DELETE /mcp — session termination.

   Removes the session from the store.
   Returns 405 if the server doesn't allow client-initiated session termination."
  [request session-store]
  (let [session-id (find-header request "MCP-Session-Id")]
    (if (nil? session-id)
      (json-response 400 {:error "MCP-Session-Id header required"})
      (if (delete-session! session-store session-id)
        {:status 200 :headers {} :body ""}
        (json-response 404 {:error "Session not found"})))))

;; ─── Public API ─────────────────────────────────────────────────────────────

(defn create-handler
  "Create an http-kit handler function for Streamable HTTP transport.

   dispatch-fn: (fn [json-rpc-message session-id] response-map-or-nil)
     - response-map-or-nil: JSON-RPC response map, or nil for notifications
     - The dispatch-fn is responsible for JSON-RPC routing and execution

   Options:
     :session-store    — atom for session management (default: creates new one)
     :allowed-origins  — vector of allowed Origin headers (default: [] = allow all)
     :session-ttl-ms   — session time-to-live in milliseconds (default: 3600000 = 1 hour)

   Returns a function suitable for http-kit/run-server."
  [dispatch-fn & [{:keys [session-store allowed-origins session-ttl-ms]
                   :or {session-store (create-session-store)
                        allowed-origins []
                        session-ttl-ms (* 60 60 1000)}}]]
  ;; Start a background thread for session pruning
  (let [pruning-thread (Thread.
                        (fn []
                          (while (not (.isInterrupted (Thread/currentThread)))
                            (Thread/sleep (* 5 60 1000)) ; prune every 5 minutes
                            (prune-expired-sessions! session-store session-ttl-ms))))]
    (.setDaemon pruning-thread true)
    (.start pruning-thread)

    (fn [request]
      (let [uri (:uri request)]
        (cond
          ;; MCP endpoint — handles POST, GET, DELETE
          (or (= uri "/mcp") (= uri "/mcp/"))
          (case (:request-method request)
            :post (handle-post request dispatch-fn session-store allowed-origins)
            :get (handle-get request session-store allowed-origins)
            :delete (handle-delete request session-store)
            {:status 405
             :headers {"Content-Type" "text/plain"}
             :body "Method Not Allowed"})

          ;; Health check
          (= uri "/health")
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body (json/generate-string {:status "ok"})}

          ;; Not found
          :else
          {:status 404
           :headers {"Content-Type" "text/plain"}
           :body "Not Found"})))))

(defn run-server
  "Start an MCP server with Streamable HTTP transport.

   dispatch-fn: (fn [json-rpc-message session-id] response-map-or-nil)
   Options:
     :port             — HTTP port (default: 0 = random available port)
     :host             — HTTP host (default: \"127.0.0.1\")
     :session-store    — atom for session management
     :allowed-origins  — vector of allowed Origin headers
     :session-ttl-ms   — session TTL in milliseconds

   Returns the server map (with :local-port in metadata)."
  [dispatch-fn & [{:keys [port host]
                   :or {port 0 host "127.0.0.1"}
                   :as opts}]]
  (let [handler (create-handler dispatch-fn opts)]
    (http/run-server handler {:port port :ip host})))
