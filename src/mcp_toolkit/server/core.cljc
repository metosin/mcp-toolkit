(ns mcp-toolkit.server.core
  (:require [mate.core :as mc]
            [mcp-toolkit.json-rpc.handler :as json-rpc.handler]
            [mcp-toolkit.json-rpc.message :as json-rpc.message]
            [mcp-toolkit.server.impl.handler :as server.handler]
            [promesa.core :as p]))

(defn- user-callback [callback-key]
  (fn [context]
    (when-some [callback (-> context :session deref (get callback-key))]
      (callback context))))

;;
;; Functions typically called from a prompt-fn or a tool-fn
;;

(defn notify-progress [context progress]
  (let [{:keys [message]} context]
    (when-some [progress-token (-> message :params :_meta :progressToken)]
      (json-rpc.handler/send-message context (json-rpc.message/notification "progress"
                                                                            (-> {:progressToken progress-token}
                                                                                (into progress))))))
  nil)

(def ^:private log-level->importance
  {"debug"     0
   "info"      1
   "notice"    2
   "warning"   3
   "error"     4
   "critical"  5
   "alert"     6
   "emergency" 7})

(defn notify-log [context level logger data]
  (let [{:keys [session]} context
        logging-level (:logging-level @session)]
    (when (>= (log-level->importance level -1) (log-level->importance logging-level))
      (json-rpc.handler/send-message context (json-rpc.message/notification "message"
                                                                            {:level  level
                                                                             :logger logger
                                                                             :data   data}))))
  nil)

(defn request-root-list [context]
  (let [{:keys [session]} context
        {:keys [client-capabilities]} @session]
    (when (contains? client-capabilities :roots)
      (-> (json-rpc.handler/call-remote-method context {:method "roots/list"})
          (p/then (fn [result]
                    (swap! session assoc :client-root-by-uri (mc/index-by :uri (:roots result)))
                    ((user-callback :on-client-root-list-updated) context)
                    nil))))))

;; FIXME: implementation is not complete
(defn request-sampling
  "Returns a promise, either resolved with the result or rejected with the error."
  [context params]
  (let [{:keys [session]} context
        {:keys [client-capabilities]} @session]
    (when (contains? client-capabilities :sampling)
      (json-rpc.handler/call-remote-method context {:method "sampling/createMessage"
                                                    :params params}))))

;;
;; Functions typically called by hand from a REPL session while working on MCP tooling
;;

(defn notify-prompts-updated [context]
  (json-rpc.handler/send-message context (json-rpc.message/notification "prompt/list_changed"))
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
  (let [{:keys [session]} context
        {:keys [client-subscribed-resource-uris]} @session
        {:keys [uri]} resource]
    (when (contains? client-subscribed-resource-uris uri)
      (json-rpc.handler/send-message context (json-rpc.message/notification "resources/updated"
                                                                            {:uri uri}))))
  nil)

(defn notify-resources-updated [context]
  (json-rpc.handler/send-message context (json-rpc.message/notification "resources/list_changed"))
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
  (json-rpc.handler/send-message context (json-rpc.message/notification "tools/list_changed"))
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

;;
;;
;;

(defn create-session
  "Returns the state of a newly created session."
  [{:keys [server-info
           server-instructions

           ;; MCP server features
           prompts
           resources
           tools
           resource-templates
           resource-uri-complete-fn
           logging-level
           on-client-root-list-changed ;; called after the server get the notification from the client
           on-client-root-list-updated ;; called after the server updated its data
           ,]
    :or   {server-info                 {:name    "mcp-toolkit"
                                        :version "0.0.1"}
           logging-level               "info"
           on-client-root-list-changed request-root-list}}]
  {;; About the server
   :server-supported-protocol-versions ["2024-11-05"
                                        "2025-03-26"]
   :server-info                        server-info
   :server-instructions                server-instructions

   :initialized                        false
   :handler-by-method                  server.handler/handler-by-method-pre-initialization

   :protocol-version                   nil ; determined at initialization
   :prompt-by-name                     (mc/index-by :name prompts)
   :resource-by-uri                    (mc/index-by :uri resources)
   :tool-by-name                       (mc/index-by :name tools)
   :resource-templates                 resource-templates
   :resource-uri-complete-fn           resource-uri-complete-fn
   :is-cancelled-by-request-id         {} ;; "is-cancelled" atoms indexed by request-id
   :logging-level                      logging-level
   :on-client-root-list-changed        on-client-root-list-changed
   :on-client-root-list-updated        on-client-root-list-updated

   ;; About the client
   :client-info                        nil
   :client-capabilities                nil
   :client-subscribed-resource-uris    #{}
   :client-root-by-uri                 {}

   :last-called-method-id              -1 ;; Used for calling methods on the remote site
   :handler-by-called-method-id        {} ;; The response handlers
   ,})
