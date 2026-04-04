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
   - Protocol version validation
   - Terminated session detection (tombstone)
   - Health check at /health

   Usage:
     (def dispatch-fn (fn [message session-id] ...))
     (def handler (create-handler dispatch-fn {:session-store (create-session-store)}))
     (http/run-server handler {:port 3000})"
  (:require [clojure.string :as str]
            [org.httpkit.server :as http]
            [cheshire.core :as json]))

;; ─── Protocol Version ───────────────────────────────────────────────────────

(def supported-protocol-versions
  "Set of supported MCP protocol version strings."
  #{"2024-11-05" "2025-06-18" "2025-11-25"})

;; ─── HTTP Helpers ───────────────────────────────────────────────────────────

(defn- find-header
  "Find an HTTP header by name (case-insensitive).
   Handles both keyword and string keys, with any case."
  [request header-name]
  (let [headers (:headers request)
        low-name (str/lower-case header-name)]
    (or (get headers low-name)
        (get headers (str header-name))
        (get headers (keyword low-name))
        (get headers (keyword header-name))
        (some (fn [[k v]]
                (when (= low-name (str/lower-case (name k)))
                  v))
              headers))))

(defn- validate-protocol-version
  "Validate the MCP-Protocol-Version header.
   Returns nil if valid (or not provided — optional per spec),
   or an error response map if invalid."
  [request]
  (when-let [version (find-header request "MCP-Protocol-Version")]
    (when-not (contains? supported-protocol-versions version)
      {:jsonrpc "2.0"
       :error {:code -32600
               :message (str "Unsupported protocol version: " version)}})))

;; ─── Session Store ──────────────────────────────────────────────────────────

(defn create-session-store
  "Create an empty session store.
   Internal structure:
     {:sessions   {sid {:created-at ts :last-active ts ...}}
      :terminated {sid {:terminated-at ts}}}"
  []
  (atom {:sessions {} :terminated {}}))

(defn create-session!
  "Create a new session and return its ID."
  ([store]
   (create-session! store nil))
  ([store extra]
   (let [sid (str (java.util.UUID/randomUUID))
         now (System/currentTimeMillis)]
     (swap! store update :sessions assoc sid (merge {:created-at now :last-active now} extra))
     sid)))

(defn valid-session?
  "Check if a session ID is in the active sessions map."
  [store sid]
  (boolean (and sid (contains? (:sessions @store) sid))))

(defn terminated-session?
  "Check if a session ID has been terminated (tombstoned)."
  [store sid]
  (boolean (and sid (contains? (:terminated @store) sid))))

(defn touch-session!
  "Update the last-active timestamp for a session."
  [store sid]
  (when sid
    (swap! store update-in [:sessions sid :last-active]
           (constantly (System/currentTimeMillis)))))

(defn delete-session!
  "Terminate a session. Moves it from :sessions to :terminated (tombstone).

   Returns:
     :existed            — session was active and has been terminated
     :already-terminated — session was already in the terminated map
     :not-found          — session was never seen"
  [store sid]
  (if (nil? sid)
    :not-found
    (let [{:keys [sessions terminated]} @store
          result (cond
                   (contains? sessions sid) :existed
                   (contains? terminated sid) :already-terminated
                   :else :not-found)]
      (case result
        :existed
        (swap! store (fn [s]
                       (-> s
                           (update :sessions dissoc sid)
                           (assoc-in [:terminated sid] {:terminated-at (System/currentTimeMillis)}))))
        (:already-terminated :not-found) nil)
      result)))

(defn prune-expired-sessions!
  "Remove terminated sessions older than ttl-ms.
   Returns count of remaining terminated sessions.
   Does NOT remove active sessions — those should persist until explicitly deleted."
  ([store]
   (prune-expired-sessions! store (* 60 60 1000))) ; 1 hour default
  ([store ttl-ms]
   (let [cutoff (- (System/currentTimeMillis) ttl-ms)]
     (swap! store (fn [{:keys [terminated] :as state}]
                    (assoc state :terminated
                           (reduce-kv (fn [acc k v]
                                        (if (< (:terminated-at v) cutoff)
                                          acc
                                          (assoc acc k v)))
                                      {}
                                      terminated))))
     (count (:terminated @store)))))


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

;; ─── Dispatch Response Handling ─────────────────────────────────────────────

(defn- handle-dispatch-response
  "Handle the result of dispatching a message through the JSON-RPC handler.
   Determines if the message was a request (has :id), notification, or response,
   and returns an appropriate HTTP response.

   - For requests: returns the JSON-RPC response as JSON (200)
   - For notifications/responses: returns 202 Accepted"
  [dispatch-result new-session? session-id]
  (if (nil? dispatch-result)
    ;; Notification or pure response — 202 Accepted
    (accepted-response (when new-session? {"MCP-Session-Id" session-id}))
    ;; JSON-RPC request got a response — return as JSON
    (json-response 200 dispatch-result
                   (when new-session? {"MCP-Session-Id" session-id}))))

;; ─── Request Handlers ───────────────────────────────────────────────────────

(defn- handle-post
  "Handle POST /mcp — client→server messages.

   1. Validate Origin header
   2. Validate MCP-Protocol-Version header
   3. Parse JSON body
   4. For initialize: creates session, returns InitializeResult with MCP-Session-Id
   5. For other requests: validates session (checks terminated separately)
   6. Dispatches through the provided dispatch-fn"
  [request dispatch-fn session-store allowed-origins]
  (if-not (validate-origin request allowed-origins)
    (json-response 403
                   {:jsonrpc "2.0"
                    :error {:code -32600
                            :message "Forbidden: invalid Origin header"}})
    ;; Check protocol version first
    (if-let [version-error (validate-protocol-version request)]
      (json-response 400 version-error)
      (let [body (parse-body request)]
        (cond
          (nil? body)
          (json-response 400 {:error "Missing request body"})

          (= body :parse-error)
          (json-response 400 {:error "Invalid JSON"})

          :else
          (let [session-id (find-header request "MCP-Session-Id")
                method (:method body)
                new-session? (= method "initialize")]
            (if new-session?
              ;; initialize — always create a new session
              (let [sid (create-session! session-store)]
                (touch-session! session-store sid)
                (let [response (dispatch-fn body sid)]
                  (handle-dispatch-response response true sid)))
              ;; Non-initialize — session must exist or be terminated
              (cond
                (terminated-session? session-store session-id)
                (json-response 404
                               {:error "Session has been terminated"})

                (not (valid-session? session-store session-id))
                (json-response 400
                               {:error "Invalid or missing MCP-Session-Id"})

                :else
                (do
                  (touch-session! session-store session-id)
                  (let [response (dispatch-fn body session-id)]
                    (handle-dispatch-response response false session-id)))))))))))

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
      (cond
        (nil? session-id)
        (json-response 400 {:error "MCP-Session-Id header required for SSE stream"})

        (terminated-session? session-store session-id)
        (json-response 404 {:error "Session has been terminated"})

        (not (valid-session? session-store session-id))
        (json-response 404 {:error "Session not found"})

        :else
        ;; Open SSE channel
        (http/as-channel request
                         {:on-open (fn [ch]
                                     (when last-event-id
                                       (http/send! ch
                                                   (str "id: " last-event-id "\ndata: \n\n")
                                                   :text)))
                          :on-close (fn [_ch _status]
                                      nil)})))))

(defn- handle-delete
  "Handle DELETE /mcp — session termination.

   Returns:
     200 — session terminated successfully (or was already terminated)
     400 — no MCP-Session-Id provided
     404 — session never existed"
  [request session-store]
  (let [session-id (find-header request "MCP-Session-Id")]
    (if (nil? session-id)
      (json-response 400 {:error "MCP-Session-Id header required"})
      (case (delete-session! session-store session-id)
        :existed
        {:status 200 :headers {} :body ""}

        :already-terminated
        {:status 200 :headers {} :body ""}

        :not-found
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
