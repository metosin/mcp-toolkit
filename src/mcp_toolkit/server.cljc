(ns mcp-toolkit.server
  (:require [mate.core :as mc]
            [mcp-toolkit.json-rpc :as json-rpc]
            [mcp-toolkit.impl.server.handler :as server.handler]
            [mcp-toolkit.impl.common :refer [user-callback]]
            [promesa.core :as p]))

;;
;; Functions typically called from a prompt-fn or a tool-fn
;;

(defn notify-progress
  "Notifies the client about progress during tool or prompt execution.
   Only sends if the current message contains a progress token.
   (see https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/progress#progress-flow)

   Args:
     context  - The server session context
     progress - Map with progress information (e.g., {:progress 50 :total 100})

   Returns:
     nil"
  [context progress]
  (let [{:keys [message]} context]
    (when-some [progress-token (-> message :params :_meta :progressToken)]
      (json-rpc/send-message context (json-rpc/notification "progress"
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

(defn notify-log
  "Sends a log message to the client if it meets the current logging level threshold.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/logging#log-message-notifications)

   Args:
     context - The server session context
     level   - Logging level, accepted values are \"debug\", \"info\", \"notice\", \"warning\", \"error\", \"critical\", \"alert\" and \"emergency\"
     logger  - Logger name/identifier string
     data    - Log message data (typically a string)

   Returns:
     nil"
  [context level logger data]
  (let [{:keys [session]} context
        logging-level (:logging-level @session)]
    (when (>= (log-level->importance level -1) (log-level->importance logging-level))
      (json-rpc/send-message context (json-rpc/notification "message"
                                                            {:level  level
                                                             :logger logger
                                                             :data   data}))))
  nil)

(defn request-root-list
  "Requests the list of root directories from the MCP client.
   Updates the session's client-root-by-uri index and calls the
   on-client-root-list-updated callback.
   (see https://modelcontextprotocol.io/specification/2025-06-18/client/roots#listing-roots)

   Args:
     context - The server session context

   Returns:
     A promise that resolves when roots are fetched and stored."
  [context]
  (let [{:keys [session]} context
        {:keys [client-capabilities]} @session]
    (when (contains? client-capabilities :roots)
      (-> (json-rpc/call-remote-method context {:method "roots/list"})
          (p/then (fn [result]
                    (swap! session assoc :client-root-by-uri (mc/index-by :uri (:roots result)))
                    ((user-callback :on-client-root-list-updated) context)
                    nil))))))

;; FIXME: implementation is not complete
(defn request-sampling
  "Requests message sampling from the MCP client.
   Returns a promise, either resolved with the result or rejected with the error.
   (see https://modelcontextprotocol.io/specification/2025-06-18/client/sampling#creating-messages)

   Args:
     context - The server session context
     params  - Sampling parameters map

   Returns:
     A promise that resolves to the sampling result from the client."
  [context params]
  (let [{:keys [session]} context
        {:keys [client-capabilities]} @session]
    (when (contains? client-capabilities :sampling)
      (json-rpc/call-remote-method context {:method "sampling/createMessage"
                                            :params params}))))

;;
;; Functions typically called by hand from a REPL session while working on MCP tooling
;;

(defn notify-prompt-list-changed
  "Notifies the client that the server's prompt list has changed.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/prompts#list-changed-notification)

   Args:
     context - The server session context

   Returns:
     nil"
  [context]
  (json-rpc/send-message context (json-rpc/notification "prompt/list_changed"))
  nil)

(defn add-prompt
  "Adds a prompt to the server's prompt registry and notifies the client.

   Args:
     context - The server session context
     prompt  - Prompt map with :name key and other prompt configuration

   Returns:
     nil"
  [context prompt]
  (let [{:keys [session]} context]
    (swap! session update :prompt-by-name assoc (:name prompt) prompt)
    (notify-prompt-list-changed context))
  nil)

(defn remove-prompt
  "Removes a prompt from the server's prompt registry and notifies the client.

   Args:
     context - The server session context
     prompt  - Prompt map with :name key to identify which prompt to remove

   Returns:
     nil"
  [context prompt]
  (let [{:keys [session]} context]
    (swap! session update :prompt-by-name dissoc (:name prompt))
    (notify-prompt-list-changed context))
  nil)

(defn notify-resource-updated
  "Notifies subscribed clients about a specific resource update.
   Only sends notification if the client is subscribed to the resource URI.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/resources#subscriptions)

   Args:
     context  - The server session context
     resource - Resource map with :uri key

   Returns:
     nil"
  [context resource]
  (let [{:keys [session]} context
        {:keys [client-subscribed-resource-uris]} @session
        {:keys [uri]} resource]
    (when (contains? client-subscribed-resource-uris uri)
      (json-rpc/send-message context (json-rpc/notification "resources/updated"
                                                            {:uri uri}))))
  nil)

(defn notify-resource-list-changed
  "Notifies the client that the server's resource list has changed.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/resources#list-changed-notification)

   Args:
     context - The server session context

   Returns:
     nil"
  [context]
  (json-rpc/send-message context (json-rpc/notification "resources/list_changed"))
  nil)

(defn add-resource
  "Adds a resource to the server's resource registry and notifies the client.

   Args:
     context  - The server session context
     resource - Resource map with :uri key and other resource configuration

   Returns:
     nil"
  [context resource]
  (let [{:keys [session]} context]
    (swap! session update :resource-by-uri assoc (:uri resource) resource)
    (notify-resource-list-changed context))
  nil)

(defn remove-resource
  "Removes a resource from the server's resource registry and notifies the client.

   Args:
     context  - The server session context
     resource - Resource map with :uri key to identify which resource to remove

   Returns:
     nil"
  [context resource]
  (let [{:keys [session]} context]
    (swap! session update :resource-by-uri dissoc (:uri resource))
    (notify-resource-list-changed context))
  nil)

(defn notify-tool-list-changed
  "Notifies the client that the server's tool list has changed.
  (see https://modelcontextprotocol.io/specification/2025-06-18/server/tools#list-changed-notification)

   Args:
     context - The server session context

   Returns:
     nil"
  [context]
  (json-rpc/send-message context (json-rpc/notification "tools/list_changed"))
  nil)

(defn add-tool
  "Adds a tool to the server's tool registry and notifies the client.

   Args:
     context - The server session context
     tool    - Tool map with :name key and other tool configuration

   Returns:
     nil"
  [context tool]
  (let [{:keys [session]} context]
    (swap! session update :tool-by-name assoc (:name tool) tool)
    (notify-tool-list-changed context))
  nil)

(defn remove-tool
  "Removes a tool from the server's tool registry and notifies the client.

   Args:
     context - The server session context
     tool    - Tool map with :name key to identify which tool to remove

   Returns:
     nil"
  [context tool]
  (let [{:keys [session]} context]
    (swap! session update :tool-by-name dissoc (:name tool))
    (notify-tool-list-changed context))
  nil)

(defn set-resource-templates
  "Sets the resource templates for the server session.

   Args:
     context            - The server session context
     resource-templates - Vector of resource template maps

   Returns:
     nil"
  [context resource-templates]
  (let [{:keys [session]} context]
    (swap! session assoc :resource-templates resource-templates))
  nil)

(defn set-resource-uri-complete-fn
  "Sets the resource URI completion function for the server session.

   Args:
     context                   - The server session context
     resource-uri-complete-fn  - Function to handle resource URI completion requests

   Returns:
     nil"
  [context resource-uri-complete-fn]
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
           on-initialized
           on-client-root-list-changed ;; called after the server get the notification from the client
           on-client-root-list-updated ;; called after the server updated its data
           ,]
    :or   {server-info                 {:name    "mcp-toolkit"
                                        :version "0.1.0-alpha"}
           logging-level               "info"
           on-initialized              request-root-list
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

   :on-initialized                     on-initialized
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
