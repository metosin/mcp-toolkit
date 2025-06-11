(ns mcp-toolkit.server.core
  (:require [mate.core :as mc]
            [promesa.core :as p]
            [jsonista.core :as j]
            [mcp-toolkit.server.json-rpc-message :as json-rpc]
            [mcp-toolkit.server.handler :as handler])
  (:import (clojure.lang LineNumberingPushbackReader)
           (java.io OutputStreamWriter)))

(def ^:private log-level->importance
  {"debug"     0
   "info"      1
   "notice"    2
   "warning"   3
   "error"     4
   "critical"  5
   "alert"     6
   "emergency" 7})

(defn create-session
  "Returns the state of a newly created session inside an atom."
  [{:keys [server-info
           server-instructions

           ;; MCP server features
           prompts
           resources
           tools
           resource-templates
           resource-uri-complete-fn
           on-client-roots-updated
           client-logging-level]
    :or {server-info {:name    "mcp-toolkit"
                      :version "0.0.1"}
         client-logging-level "info"}}]
  (atom {;; About the server
         :server-supported-protocol-versions ["2024-11-05"
                                              "2025-03-26"]
         :server-info                        server-info
         :server-instructions                server-instructions

         :initialized                        false
         :protocol-version                   nil ; determined at initialization
         :prompt-by-name                     (mc/index-by :name prompts)
         :resource-by-uri                    (mc/index-by :uri resources)
         :tool-by-name                       (mc/index-by :name tools)
         :resource-templates                 resource-templates
         :resource-uri-complete-fn           resource-uri-complete-fn
         :is-cancelled-by-message-id         {} ;; "is-cancelled" atoms indexed by message-id
         :on-client-roots-updated            on-client-roots-updated

         ;; About the client
         :client-info                        nil
         :client-capabilities                nil
         :client-subscribed-resource-uris    #{}
         :client-root-by-uri                 {}
         :client-logging-level               client-logging-level

         :last-used-client-method-id         -1
         :handler-by-client-method-id        {}}))

(defn- route-message
  "Returns a Promesa promise which handles a given json-rpc-message."
  [{:keys [session message] :as context}]
  (if (contains? message :method)
    (let [handler-name->handler (if (:initialized @session)
                                  {"ping"                             handler/ping-handler
                                   "logging/setLevel"                 handler/set-logging-level-handler
                                   "completion/complete"              handler/completion-complete-handler
                                   "prompts/list"                     handler/prompt-list-handler
                                   "prompts/get"                      handler/prompt-get-handler
                                   "resources/list"                   handler/resource-list-handler
                                   "resources/read"                   handler/resource-read-handler
                                   "resources/templates/list"         handler/resource-templates-list-handler
                                   "resources/subscribe"              handler/resource-subscribe-handler
                                   "resources/unsubscribe"            handler/resource-unsubscribe-handler
                                   "tools/list"                       handler/tool-list-handler
                                   "tools/call"                       handler/tool-call-handler
                                   "notifications/cancelled"          handler/cancelled-notification-handler
                                   "notifications/roots/list_changed" handler/roots-changed-notification-handler}
                                  {"ping"                      handler/ping-handler
                                   "initialize"                handler/initialize-handler
                                   "notifications/initialized" handler/initialized-notification-handler})
          {:keys [id method]} message
          handler (handler-name->handler method)]
      (if (nil? handler)
        (json-rpc/method-not-found-response id)
        (if (nil? id)
          ;; Notification, shall not return a result
          (do
            (handler context)
            nil)
          ;; Method call, cancellable, with result value when not cancelled
          (let [is-cancelled (atom false)
                context (assoc context :is-cancelled is-cancelled)]
            (swap! session update :is-cancelled-by-message-id assoc id is-cancelled)
            (-> (handler context)
                (p/then (fn [result]
                          (when-not @is-cancelled
                            {:jsonrpc "2.0"
                             :result result
                             :id id})))
                (p/handle (fn [result error]
                            ;; Clean up, side effect
                            (swap! session update :is-cancelled-by-message-id dissoc id)

                            ;; Pass through as if this p/handle was not there.
                            ;; We avoided using p/finally because it does not allow chaining further promises.
                            (or error result))))))))
    ;; Method call response
    (if (and (contains? message :id)
             (or (contains? message :result)
                 (contains? message :error)))
      (if-some [handler (-> @session :handler-by-client-method-id (get (:id message)))]
        (do
          (handler context)
          nil)
        ;; TODO: handle the case where the id is unknown to us.
        ,)
      ;; TODO: handle the message's structural problem.
      ,)))

(defn- handle-message [{:keys [message send-message] :as context}]
  (if (vector? message)
    ;; It is a batch message, if we respond it should be a batch response
    (let [batch-response (->> message
                              (mapv (fn [message]
                                      (route-message (assoc context :message message)))))]
      (-> (p/all batch-response)
          (p/then (fn [batch-response]
                    (let [batch-response (filterv some? batch-response)]
                      (when (seq batch-response)
                        (send-message batch-response)))))))
    ;; It is a single message
    (-> (route-message context)
        (p/then (fn [response]
                  (when (some? response)
                    (send-message response)))))))

(defn listen-messages [context]
  (let [{:keys [session read-message send-message]} context]
    (loop []
      (when-some [message (read-message)]
        (swap! session update :message-log conj [:--> message])
        ;;(if invalid-request
        ;;  (send-message (rpc/invalid-request-response))
        (let [context {:session session
                       :message message
                       :send-message send-message}]
          (handle-message context))
        (recur)))))

;; --- STDIO transport

(defn create-stdio-context [session
                            ^LineNumberingPushbackReader reader
                            ^OutputStreamWriter writer]
  (let [json-mapper (j/object-mapper {:encode-key-fn name
                                      :decode-key-fn keyword})
        send-message (fn [message]
                       (swap! session update :message-log conj [:<-- message])
                       (.write writer (j/write-value-as-string message json-mapper))
                       (.write writer "\n")
                       (.flush writer))
        read-message (fn []
                       (loop []
                         ;; line = nil means that the reader is closed
                         (when-some [line (.readLine reader)]
                           (let [message (try
                                           (j/read-value line json-mapper)
                                           (catch Exception e
                                             (send-message json-rpc/parse-error-response)
                                             nil))]
                             (if (nil? message)
                               (recur)
                               message)))))]
    (swap! session assoc :message-log [])
    {:session session
     :send-message send-message
     :read-message read-message}))

;; --- Typically called from a tool-fn

(defn notify-progress [context progress]
  (let [{:keys [message send-message]} context]
    (when-some [progress-token (-> message :params :_meta :progressToken)]
      (send-message (json-rpc/notification "progress"
                                           (-> {:progressToken progress-token}
                                               (into progress)))))))

;; --- Typically called by hand from a REPL session

(defn add-prompt [context prompt]
  (let [{:keys [session send-message]} context]
    (swap! session update :prompt-by-name assoc (:name prompt) prompt)
    (send-message (json-rpc/notification "prompt/list_changed")))
  nil)

(defn remove-prompt [context prompt]
  (let [{:keys [session send-message]} context]
    (swap! session update :prompt-by-name dissoc (:name prompt))
    (send-message (json-rpc/notification "prompt/list_changed")))
  nil)

(defn add-resource [context resource]
  (let [{:keys [session send-message]} context]
    (swap! session update :resource-by-uri assoc (:uri resource) resource)
    (send-message (json-rpc/notification "resources/list_changed")))
  nil)

(defn remove-resource [context resource]
  (let [{:keys [session send-message]} context]
    (swap! session update :resource-by-uri dissoc (:uri resource))
    (send-message (json-rpc/notification "resources/list_changed")))
  nil)

(defn notify-resource-updated [context resource]
  (let [{:keys [session send-message]} context
        {:keys [client-subscribed-resource-uris]} @session
        {:keys [uri]} resource]
    (when (contains? client-subscribed-resource-uris uri)
      (send-message (json-rpc/notification "resources/updated"
                                           {:uri uri}))))
  nil)

(defn add-tool [context tool]
  (let [{:keys [session send-message]} context]
    (swap! session update :tool-by-name assoc (:name tool) tool)
    (send-message (json-rpc/notification "tools/list_changed")))
  nil)

(defn remove-tool [context tool]
  (let [{:keys [session send-message]} context]
    (swap! session update :tool-by-name dissoc (:name tool))
    (send-message (json-rpc/notification "tools/list_changed")))
  nil)

(defn set-resource-templates [context resource-templates]
  (let [{:keys [session]} context]
    (swap! session assoc :resource-templates resource-templates))
  nil)

(defn set-resource-uri-complete-fn [context resource-uri-complete-fn]
  (let [{:keys [session]} context]
    (swap! session assoc :resource-uri-complete-fn resource-uri-complete-fn))
  nil)

(defn request-sampling
  "Returns a promise, either resolved with the result or rejected with the error."
  [context params]
  (let [{:keys [session]} context]
    (when (contains? (:client-capabilities @session) :sampling)
      (handler/call-remote-method context {:method "sampling/createMessage"
                                           :params params}))))

(defn send-log-data [context level logger data]
  (let [{:keys [session send-message]} context
        client-logging-level (:client-logging-level @session)]
    (when (>= (log-level->importance level -1) (log-level->importance client-logging-level))
      (send-message (json-rpc/notification "message"
                                           {:level level
                                            :logger logger
                                            :data data})))))
