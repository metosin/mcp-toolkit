(ns mcp-toolkit.server.core
  (:require [mate.core :as mc]
            [mcp-toolkit.json-rpc.message :as json-rpc]
            [mcp-toolkit.server.handler :as handler]))

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
         :handler-by-method                  handler/handler-by-method-pre-initialization

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

         :last-called-method-id              -1 ;; Used for calling methods on the remote site
         :handler-by-called-method-id        {} ;; The response handlers
         ,}))

;;
;; Functions typically called from a prompt-fn or a tool-fn
;;

(defn notify-progress [context progress]
  (let [{:keys [message send-message]} context]
    (when-some [progress-token (-> message :params :_meta :progressToken)]
      (send-message (json-rpc/notification "progress"
                                           (-> {:progressToken progress-token}
                                               (into progress)))))))

(def ^:private log-level->importance
  {"debug"     0
   "info"      1
   "notice"    2
   "warning"   3
   "error"     4
   "critical"  5
   "alert"     6
   "emergency" 7})

(defn send-log-data [context level logger data]
  (let [{:keys [session send-message]} context
        client-logging-level (:client-logging-level @session)]
    (when (>= (log-level->importance level -1) (log-level->importance client-logging-level))
      (send-message (json-rpc/notification "message"
                                           {:level level
                                            :logger logger
                                            :data data})))))

(defn request-sampling
  "Returns a promise, either resolved with the result or rejected with the error."
  [context params]
  (let [{:keys [session]} context]
    (when (contains? (:client-capabilities @session) :sampling)
      (handler/call-remote-method context {:method "sampling/createMessage"
                                           :params params}))))

;;
;; Functions typically called by hand from a REPL session while working on MCP tooling
;;

(defn notify-prompts-updated [context]
  (let [{:keys [send-message]} context]
    (send-message (json-rpc/notification "prompt/list_changed")))
  nil)

(defn add-prompt [context prompt]
  (let [{:keys [session]} context]
    (swap! session update :prompt-by-name assoc (:name prompt) prompt)
    (notify-prompts-updated context))
  nil)

(defn remove-prompt [context prompt]
  (let [{:keys [session]} context]
    (swap! session update :prompt-by-name dissoc (:name prompt))
    (notify-prompts-updated context))
  nil)

(defn notify-resource-updated [context resource]
  (let [{:keys [session send-message]} context
        {:keys [client-subscribed-resource-uris]} @session
        {:keys [uri]} resource]
    (when (contains? client-subscribed-resource-uris uri)
      (send-message (json-rpc/notification "resources/updated"
                                           {:uri uri}))))
  nil)

(defn notify-resources-updated [context]
  (let [{:keys [send-message]} context]
    (send-message (json-rpc/notification "resources/list_changed")))
  nil)

(defn add-resource [context resource]
  (let [{:keys [session]} context]
    (swap! session update :resource-by-uri assoc (:uri resource) resource)
    (notify-resources-updated context))
  nil)

(defn remove-resource [context resource]
  (let [{:keys [session]} context]
    (swap! session update :resource-by-uri dissoc (:uri resource))
    (notify-resources-updated context))
  nil)

(defn notify-tools-updated [context]
  (let [{:keys [send-message]} context]
    (send-message (json-rpc/notification "tools/list_changed")))
  nil)

(defn add-tool [context tool]
  (let [{:keys [session]} context]
    (swap! session update :tool-by-name assoc (:name tool) tool)
    (notify-tools-updated context))
  nil)

(defn remove-tool [context tool]
  (let [{:keys [session]} context]
    (swap! session update :tool-by-name dissoc (:name tool))
    (notify-tools-updated context))
  nil)

(defn set-resource-templates [context resource-templates]
  (let [{:keys [session]} context]
    (swap! session assoc :resource-templates resource-templates))
  nil)

(defn set-resource-uri-complete-fn [context resource-uri-complete-fn]
  (let [{:keys [session]} context]
    (swap! session assoc :resource-uri-complete-fn resource-uri-complete-fn))
  nil)
