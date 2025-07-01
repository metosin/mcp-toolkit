(ns example.transport.sse
  "This namespaces provides a 2024-11-05 compatible SSE transport for MCP Toolkit."
  (:require
   [clojure.string :as str]
   [jsonista.core :as j]
   [mcp-toolkit.json-rpc :as json-rpc]
   [org.httpkit.server :as http-kit]
   [taoensso.telemere :as tel]))

(def object-mapper
  (j/object-mapper {:decode-key-fn keyword :encode-key-fn name}))

(defn parse-message [ctx]
  (let [message (try
                  (-> (j/read-value (-> ctx :req :body) object-mapper))
                  (catch Exception _
                    nil))]
    message))

(defn ->json [v]
  (j/write-value-as-string v object-mapper))

(defn error-response [msg status & {:keys [headers]}]
  {:status  status
   :headers {"content-type" "text/plain"}
   :body    msg})

(defn valid-content-type? [ctx]
  (let [ct (get-in ctx [:req :headers "content-type"])]
    (cond
      (str/blank? ct)                                false
      (not (str/starts-with? ct "application/json")) false
      :else                                          true)))

(defn matches-port-wildcard? [pattern value]
  (when (str/ends-with? pattern ":*")
    (let [base (subs pattern 0 (- (count pattern) 2))]
      (str/starts-with? value (str base ":")))))

(defn valid-host?
  ([host allowed-hosts]
   (cond
     (str/blank? host)              false
     (contains? allowed-hosts host) true
     :else
     (boolean (some #(matches-port-wildcard? % host) allowed-hosts))))
  ([ctx]
   (let [host          (get-in ctx [:req :headers "host"])
         allowed-hosts (get-in ctx [:settings :allowed-hosts])]
     (valid-host? host allowed-hosts))))

(defn valid-origin?
  ([origin allowed-origins]
   (cond
     (str/blank? origin)                true
     (contains? allowed-origins origin) true
     :else                              (boolean (some #(matches-port-wildcard? % origin) allowed-origins))))
  ([ctx]
   (let [origin          (get-in ctx [:req :headers "origin"])
         allowed-origins (get-in ctx [:settings :allowed-origins])]
     (valid-origin? origin allowed-origins))))

(defn validate-request [{:keys [req] :as ctx}]
  (let [post? (= :post (:request-method req))]
    (cond
      (and post? (not (valid-content-type? ctx))) (error-response "Invalid Content-Type header" 400)
      (not (valid-host? ctx))                     (error-response "Invalid Host header" 421)
      (not (valid-origin? ctx))                   (error-response "Invalid Origin header" 400)
      :else                                       nil)))

(def base-SSE-headers {"Content-Type"  "text/event-stream"
                       "Cache-Control" "no-cache, no-transform"})

(defn add-keep-alive? [ring-request]
  (let [protocol (:protocol ring-request)]
    (or (nil? protocol)
        (neg? (compare protocol "HTTP/1.1")))))

(defn sse-headers
  "Returns headers for a SSE response. It adds specific SSE headers based on the
  HTTP protocol version found in the ring `req`.

  Options:
  - `:headers`: custom headers for the response

  The SSE headers this function provides can be overriden by the optional ones.
  Be carreful with the following headers:

  - \"Cache-Control\"
  - \"Content-Type\"
  - \"Connection\"
  "
  [req & {:as opts}]
  (-> {}
      (merge base-SSE-headers)
      (cond-> (add-keep-alive? req) (assoc "Connection" "keep-alive"))
      (merge (:headers opts))))

(defn send-base-sse-response!
  "Send the response headers, this should be done as soon as the conneciton is
  open."
  [req channel  {:keys [status] :as opts}]
  (tel/log! {:level :debug :id :sse/send-headers :data {:status status}})
  (http-kit/send! channel
                  {:status  (or status 200)
                   :headers (sse-headers req opts)}
                  false))

(defn channel-send! [channel event data]
  (http-kit/send! channel
                  (str "event: " event "\n" "data: " data "\n\n")
                  false))

(defn new-session-id [] (str (java.util.UUID/randomUUID)))

(defn assoc-session!
  "Adds a new connection to the pool, returns the initial session data"
  [ctx session-id channel]
  (let [data {:session/session-id session-id
              :session/channel    channel
              :session/send!      (fn [event data]
                                    (channel-send! channel event data))
              :session/data       ((:create-session-fn ctx) ctx session-id)}]
    (swap! (::connections ctx) assoc session-id data)
    data))

(defn dissoc-session!
  "Removes the session from the pool (if it exists)"
  [ctx session-id]
  (swap! (::connections ctx) dissoc session-id)
  nil)

(defn fetch-session!
  "Fetch the session by id"
  [ctx session-id]
  (get-in @(::connections ctx) [session-id]))

(defn make-send-message [{:session/keys [send!] :as _session}]
  (fn [message]
    (tel/log! {:level :debug :id :sse/outgoing-message :data {:message message}})
    (send! "message" (->json message))))

(defn handle-message-response [session message]
  (let [mcp-context {:message      message
                     :send-message (make-send-message session)
                     :session      (:session/data session)}]

    (tel/log! {:level :debug :id :sse/accepted-message :data {:message message}})
    (json-rpc/handle-message mcp-context))
  {:status  202
   :headers {"content-type" "text/plain"}
   :body    "Accepted"})

(defn handle-sse-stream [ctx req]
  (let [ctx (assoc ctx :req req)]
    (if-let [error-response (validate-request ctx)]
      error-response
      (let [session-id (new-session-id)]
        (http-kit/as-channel req
                             {:on-open
                              (fn [channel]
                                (tel/log! {:level :debug :id :sse/open})
                                (let [{:session/keys [send!] :as _session} (assoc-session! ctx session-id channel)]
                                  (send-base-sse-response! req channel {:status 200})
                                  (send! "endpoint" (str "/messages/" session-id))))
                              :on-close
                              (fn [_channel status]
                                (tel/log! {:level :debug :id :sse/close :data {:status status}})
                                (dissoc-session! ctx session-id))})))))

(defn handle-messages [ctx req]
  (let [ctx (assoc ctx :req req)]
    (if-let [error-response (validate-request ctx)]
      error-response
      (if-let [session (fetch-session! ctx (get-in req [:path-params :id]))]
        (if-let [message (parse-message ctx)]
          (handle-message-response session message)
          (error-response "Could not parse message" 400))
        (error-response "Session not found" 404)))))

(defn routes [ctx]
  [""
   ;; 2024-11-05 - SSE Transport
   ["/sse" {:get (partial handle-sse-stream ctx)}]
   ["/messages/:id" {:post (partial handle-messages ctx)}]])

(defn ctx-start [ctx]
  (assoc ctx ::connections (atom {})))
