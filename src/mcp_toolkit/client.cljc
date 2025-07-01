(ns mcp-toolkit.client
  (:require [mate.core :as mc]
            [mcp-toolkit.json-rpc :as json-rpc]
            [mcp-toolkit.impl.client.handler :as client.handler]
            [mcp-toolkit.impl.common :refer [user-callback]]
            [promesa.core :as p]))

(defn request-set-logging-level
  "Sets the logging level on the MCP server.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/logging#log-levels)

   Args:
     context - The client session context
     level   - Logging level, accepted values are \"debug\", \"info\", \"notice\", \"warning\", \"error\", \"critical\", \"alert\" and \"emergency\"

   Returns:
     A promise that resolves when the server acknowledges the level change."
  [context level]
  (json-rpc/call-remote-method context {:method "logging/setLevel"
                                        :params {:level level}}))

(defn request-complete-prompt-param
  "Requests autocompletion for a prompt parameter from the MCP server.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/completion#data-types)

   Args:
     context      - The client session context
     prompt-name  - Name of the prompt to complete
     param-name   - Name of the parameter to complete
     param-value  - Current partial value of the parameter

   Returns:
     A promise that resolves to completion suggestions from the server."
  [context prompt-name
   param-name param-value]
  (json-rpc/call-remote-method context {:method "completion/complete"
                                        :params {:ref {:type "ref/prompt"
                                                       :name prompt-name}
                                                 :argument {:name param-name
                                                            :value param-value}}}))

(defn request-complete-resource-uri
  "Requests autocompletion for a resource URI parameter from the MCP server.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/utilities/completion#data-types)

   Args:
     context      - The client session context
     uri-template - URI template to complete
     param-name   - Name of the parameter to complete
     param-value  - Current partial value of the parameter

   Returns:
     A promise that resolves to completion suggestions from the server."
  [context uri-template
   param-name param-value]
  (json-rpc/call-remote-method context {:method "completion/complete"
                                        :params {:ref {:type "ref/resource"
                                                       :uri uri-template}
                                                 :argument {:name param-name
                                                            :value param-value}}}))

(defn request-prompt-list
  "Requests the list of available prompts from the MCP server.
   Updates the session's server-prompt-by-name index and calls the
   on-server-prompt-list-updated callback.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/prompts#listing-prompts)

   Args:
     context - The client session context

   Returns:
     A promise that resolves when prompts are fetched and stored."
  [context]
  (let [{:keys [session]} context
        {:keys [server-capabilities]} @session]
    (when (contains? server-capabilities :prompts)
      (-> (json-rpc/call-remote-method context {:method "prompts/list"})
          (p/then (fn [{:keys [prompts]}]
                    (swap! session assoc :server-prompt-by-name (mc/index-by :name prompts))
                    ((user-callback :on-server-prompt-list-updated) context)))))))

(defn request-prompt
  "Requests a specific prompt from the MCP server with given arguments.
  (see https://modelcontextprotocol.io/specification/2025-06-18/server/prompts#getting-a-prompt)

   Args:
     context     - The client session context
     prompt-name - Name of the prompt to retrieve
     arguments   - Map of arguments to pass to the prompt

   Returns:
     A promise that resolves to the prompt response from the server."
  [context prompt-name arguments]
  (json-rpc/call-remote-method context {:method "prompts/get"
                                        :params {:name prompt-name
                                                 :arguments arguments}}))

(defn request-resource-list
  "Requests the list of available resources from the MCP server.
   Updates the session's server-resource-by-uri index and calls the
   on-server-resource-list-updated callback.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/resources#listing-resources)

   Args:
     context - The client session context

   Returns:
     A promise that resolves when the resource descriptions are fetched and stored."
  [context]
  (let [{:keys [session]} context
        {:keys [server-capabilities]} @session]
    (when (contains? server-capabilities :resources)
      (-> (json-rpc/call-remote-method context {:method "resources/list"})
          (p/then (fn [{:keys [resources] :as result}]
                    (swap! session assoc :server-resource-by-uri (mc/index-by :uri resources))
                    ((user-callback :on-server-resource-list-updated) context)))))))

(defn request-resource
  "Requests a specific resource from the MCP server by URI.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/resources#reading-resources)

   Args:
     context      - The client session context
     resource-uri - URI of the resource to retrieve

   Returns:
     A promise that resolves to the resource content from the server."
  [context resource-uri]
  (json-rpc/call-remote-method context {:method "resources/read"
                                        :params {:uri resource-uri}}))

(defn request-resource-template-list
  "Requests the list of available resource templates from the MCP server.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/resources#resource-templates)

   Args:
     context - The client session context

   Returns:
     A promise that resolves to the list of resource templates."
  [context]
  (json-rpc/call-remote-method context {:method "resources/templates/list"}))

(defn request-subscribe-resource
  "Subscribes to changes for a specific resource on the MCP server.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/resources#subscriptions)

   Args:
     context      - The client session context
     resource-uri - URI of the resource to subscribe to

   Returns:
     A promise that resolves when subscription is confirmed."
  [context resource-uri]
  (json-rpc/call-remote-method context {:method "resources/subscribe"
                                        :params {:uri resource-uri}}))

(defn request-unsubscribe-resource
  "Unsubscribes from changes for a specific resource on the MCP server.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/resources#subscriptions)

   Args:
     context      - The client session context
     resource-uri - URI of the resource to unsubscribe from

   Returns:
     A promise that resolves when unsubscription is confirmed."
  [context resource-uri]
  (json-rpc/call-remote-method context {:method "resources/unsubscribe"
                                        :params {:uri resource-uri}}))

(defn request-tool-list
  "Requests the list of available tools from the MCP server.
   Updates the session's server-tool-by-name index and triggers the
   on-server-tool-list-updated callback.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/tools#listing-tools)

   Args:
     context - The client session context

   Returns:
     A promise that resolves when the tool descriptions are fetched and stored."
  [context]
  (let [{:keys [session]} context
        {:keys [server-capabilities]} @session]
    (when (contains? server-capabilities :prompts)
      (-> (json-rpc/call-remote-method context {:method "tools/list"})
          (p/then (fn [{:keys [tools] :as result}]
                    (swap! session assoc :server-tool-by-name (mc/index-by :name tools))
                    ((user-callback :on-server-tool-list-updated) context)))))))

(defn request-tool-invocation
  "Invokes a specific tool on the MCP server with given arguments.
   (see https://modelcontextprotocol.io/specification/2025-06-18/server/tools#calling-tools)

   Args:
     context   - The client session context
     tool-name - Name of the tool to invoke
     arguments - Map of arguments to pass to the tool

   Returns:
     A promise that resolves to the tool execution result."
  [context tool-name arguments]
  (json-rpc/call-remote-method context {:method "tools/call"
                                        :params {:name tool-name
                                                 :arguments arguments}}))

(defn notify-cancel-request
  "Sends a cancellation notification for a specific request to the MCP server.
   (see https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/cancellation#cancellation-flow)

   Args:
     context    - The client session context
     request-id - ID of the request to cancel
    
   Returns:
     nil"
  [context request-id]
  (json-rpc/send-message context (json-rpc/notification "cancelled"
                                                        {:requestId request-id})))

(defn notify-root-list-changed
  "Notifies the MCP server that the client's root list has been changed.
   (see https://modelcontextprotocol.io/specification/2025-06-18/client/roots#root-list-changes)

   Args:
     context - The client session context

   Returns:
     nil"
  [context]
  (json-rpc/send-message context (json-rpc/notification "roots/list_changed")))

(defn add-root
  "Adds a root to the client's root registry and notifies the server.

   Args:
     context - The client session context
     root    - Root map with :uri key and other root configuration

   Returns:
     nil"
  [context root]
  (let [{:keys [session]} context]
    (swap! session update :root-by-uri assoc (:uri root) root)
    (notify-root-list-changed context))
  nil)

(defn remove-root
  "Removes a root from the client's root registry and notifies the server.

   Args:
     context - The client session context
     root    - Root map with :uri key to identify which root to remove

   Returns:
     nil"
  [context root]
  (let [{:keys [session]} context]
    (swap! session update :root-by-uri dissoc (:uri root))
    (notify-root-list-changed context))
  nil)

(defn send-first-handshake-message
  "Sends the initial handshake message to establish the MCP connection.
   Initializes the session with server capabilities and triggers the on-initialized callback
   upon receiving the server's response.
   (see https://modelcontextprotocol.io/specification/2025-06-18/architecture#capability-negotiation)

   Args:
     context - The client session context

   Returns:
     nil"
  [context]
  (let [{:keys [session]} context
        {:keys [client-info
                client-capabilities
                protocol-version]} @session]
    (-> (json-rpc/call-remote-method context {:method "initialize"
                                              :params {:clientInfo      client-info
                                                       :capabilities    client-capabilities
                                                       :protocolVersion protocol-version}})
        (p/then (fn [{:keys [protocolVersion capabilities serverInfo] :as result}]
                  (swap! session assoc
                    :server-protocol-version protocolVersion
                    :server-capabilities capabilities
                    :server-info serverInfo
                    :initialized true
                    :handler-by-method client.handler/handler-by-method-post-initialization)
                  (json-rpc/send-message context (json-rpc/notification "initialized"))
                  ((user-callback :on-initialized) context)))))
  nil)

(defn- default-on-initialized [context]
  (request-prompt-list context)
  (request-resource-list context)
  (request-tool-list context))

(defn create-session
  "Returns the state of a newly created session."
  [{:keys [client-info
           client-capabilities
           protocol-version
           roots
           on-initialized
           on-sampling-requested
           on-server-progress
           on-server-log
           on-server-prompt-list-changed
           on-server-prompt-list-updated
           on-server-resource-changed
           on-server-resource-list-changed
           on-server-resource-list-updated
           on-server-tool-list-changed
           on-server-tool-list-updated]
    :or   {client-info                     {:name    "mcp-toolkit"
                                            :version "0.1.0-alpha"}
           client-capabilities             {:roots {:listChanged true}}
           protocol-version                "2025-03-26"
           on-initialized                  default-on-initialized
           on-server-prompt-list-changed   request-prompt-list
           on-server-resource-list-changed request-resource-list
           on-server-tool-list-changed     request-tool-list}}]
  {:client-info                 client-info
   :client-capabilities         client-capabilities
   :protocol-version            protocol-version

   :initialized                 false
   :on-initialized              on-initialized
   :handler-by-method           client.handler/handler-by-method-pre-initialization

   :root-by-uri                 (mc/index-by :uri roots)

   :server-prompt-by-name       {}
   :server-resource-by-uri      {}
   :server-tool-by-name         {}

   :on-sampling-requested           on-sampling-requested
   :on-server-progress              on-server-progress
   :on-server-log                   on-server-log
   :on-server-prompt-list-changed   on-server-prompt-list-changed
   :on-server-prompt-list-updated   on-server-prompt-list-updated
   :on-server-resource-changed      on-server-resource-changed
   :on-server-resource-list-changed on-server-resource-list-changed
   :on-server-resource-list-updated on-server-resource-list-updated
   :on-server-tool-list-changed     on-server-tool-list-changed
   :on-server-tool-list-updated     on-server-tool-list-updated

   :last-called-method-id       -1 ;; Used for calling methods on the remote site
   :handler-by-called-method-id {} ;; The response handlers
   ,})
