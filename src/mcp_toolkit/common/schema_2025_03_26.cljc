(ns mcp-toolkit.common.schema-2025-03-26
  (:require [malli.core :as m]))

;; Model Context Protocol (MCP) Schema converted to Malli
;; Based on: https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/refs/heads/main/schema/2025-03-26/schema.json

;; The sender or recipient of messages and data in a conversation.
(def role
  [:enum "assistant" "user"])

;; The severity of a log message.
;; These map to syslog message severities, as specified in RFC-5424:
;; https://datatracker.ietf.org/doc/html/rfc5424#section-6.2.1
(def logging-level
  [:enum "alert" "critical" "debug" "emergency" "error" "info" "notice" "warning"])

;; A uniquely identifying ID for a request in JSON-RPC.
(def request-id
  [:or :string :int])

;; A progress token, used to associate progress notifications with the original request.
(def progress-token
  [:or :string :int])

;; An opaque token used to represent a cursor for pagination.
(def cursor :string)

;; Optional annotations for the client. The client can use annotations to inform how objects are used or displayed
(def annotations
  [:map
   [:audience {:optional true} [:vector role]]
   [:priority {:optional true} [:double {:min 0 :max 1}]]])

;; Text provided to or from an LLM.
(def text-content
  [:map
   [:type [:= "text"]]
   [:text :string]
   [:annotations {:optional true} annotations]])

;; An image provided to or from an LLM.
(def image-content
  [:map
   [:type [:= "image"]]
   [:data [:string {:description "Base64-encoded image data"}]]
   [:mime-type :string]
   [:annotations {:optional true} annotations]])

;; Audio provided to or from an LLM.
(def audio-content
  [:map
   [:type [:= "audio"]]
   [:data [:string {:description "Base64-encoded audio data"}]]
   [:mime-type :string]
   [:annotations {:optional true} annotations]])

(def text-resource-contents
  [:map
   [:uri [:string {:format :uri}]]
   [:text :string]
   [:mime-type {:optional true} :string]])

(def blob-resource-contents
  [:map
   [:uri [:string {:format :uri}]]
   [:blob [:string {:description "Base64-encoded binary data"}]]
   [:mime-type {:optional true} :string]])

;; The contents of a resource, embedded into a prompt or tool call result.
;; It is up to the client how best to render embedded resources for the benefit
;; of the LLM and/or the user.
(def embedded-resource
  [:map
   [:type [:= "resource"]]
   [:resource [:or text-resource-contents blob-resource-contents]]
   [:annotations {:optional true} annotations]])

(def content
  [:or text-content image-content audio-content embedded-resource])

;; Describes the name and version of an MCP implementation.
(def implementation
  [:map
   [:name :string]
   [:version :string]])

(def result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]])

;; Hints to use for model selection.
;; Keys not declared here are currently left unspecified by the spec and are up
;; to the client to interpret.
(def model-hint
  [:map
   [:name {:optional true} :string]])

;; The server's preferences for model selection, requested of the client during sampling.
;; Because LLMs can vary along multiple dimensions, choosing the "best" model is
;; rarely straightforward. Different models excel in different areas—some are
;; faster but less capable, others are more capable but more expensive, and so
;; on. This interface allows servers to express their priorities across multiple
;; dimensions to help clients make an appropriate selection for their use case.
;; These preferences are always advisory. The client MAY ignore them. It is also
;; up to the client to decide how to interpret these preferences and how to
;; balance them against other considerations.
(def model-preferences
  [:map
   [:cost-priority {:optional true} [:double {:min 0 :max 1}]]
   [:intelligence-priority {:optional true} [:double {:min 0 :max 1}]]
   [:speed-priority {:optional true} [:double {:min 0 :max 1}]]
   [:hints {:optional true} [:vector model-hint]]])

;; Describes a message issued to or received from an LLM API.
(def sampling-message
  [:map
   [:role role]
   [:content [:or text-content image-content audio-content]]])

;; Describes a message returned as part of a prompt.
;; This is similar to `SamplingMessage`, but also supports the embedding of
;; resources from the MCP server.
(def prompt-message
  [:map
   [:role role]
   [:content content]])

;; Describes an argument that a prompt can accept.
(def prompt-argument
  [:map
   [:name :string]
   [:description {:optional true} :string]
   [:required {:optional true} :boolean]])

;; A prompt or prompt template that the server offers.
(def prompt
  [:map
   [:name :string]
   [:description {:optional true} :string]
   [:arguments {:optional true} [:vector prompt-argument]]])

;; Identifies a prompt.
(def prompt-reference
  [:map
   [:type [:= "ref/prompt"]]
   [:name :string]])

;; A reference to a resource or resource template definition.
(def resource-reference
  [:map
   [:type [:= "ref/resource"]]
   [:uri [:string {:format :uri-template}]]])

;; A known resource that the server is capable of reading.
(def resource
  [:map
   [:name :string]
   [:uri [:string {:format :uri}]]
   [:description {:optional true} :string]
   [:mime-type {:optional true} :string]
   [:size {:optional true} :int]
   [:annotations {:optional true} annotations]])

;; A template description for resources available on the server.
(def resource-template
  [:map
   [:name :string]
   [:uri-template [:string {:format :uri-template}]]
   [:description {:optional true} :string]
   [:mime-type {:optional true} :string]
   [:annotations {:optional true} annotations]])

;; Additional properties describing a Tool to clients.
;; NOTE: all properties in ToolAnnotations are **hints**.
;; They are not guaranteed to provide a faithful description of
;; tool behavior (including descriptive properties like `title`).
;; Clients should never make tool use decisions based on ToolAnnotations
;; received from untrusted servers.
(def tool-annotations
  [:map
   [:title {:optional true} :string]
   [:read-only-hint {:optional true} :boolean]
   [:destructive-hint {:optional true} :boolean]
   [:idempotent-hint {:optional true} :boolean]
   [:open-world-hint {:optional true} :boolean]])

;; Definition for a tool the client can call.
(def tool
  [:map
   [:name :string]
   [:description {:optional true} :string]
   [:input-schema [:map
                   [:type [:= "object"]]
                   [:properties {:optional true} [:map-of :string :any]]
                   [:required {:optional true} [:vector :string]]]]
   [:annotations {:optional true} tool-annotations]])

;; Represents a root directory or file that the server can operate on.
(def root
  [:map
   [:uri [:string {:format :uri}]]
   [:name {:optional true} :string]])

;; Capabilities a client may support. Known capabilities are defined here, in this schema, but this is not a closed set:
;; any client can define its own, additional capabilities.
(def client-capabilities
  [:map
   [:experimental {:optional true} [:map-of :string :any]]
   [:roots {:optional true} [:map
                             [:list-changed {:optional true} :boolean]]]
   [:sampling {:optional true} [:map]]])

;; Capabilities that a server may support. Known capabilities are defined here, in this schema, but this is not a closed set:
;; any server can define its own, additional capabilities.
(def server-capabilities
  [:map
   [:experimental {:optional true} [:map-of :string :any]]
   [:logging {:optional true} [:map]]
   [:completions {:optional true} [:map]]
   [:prompts {:optional true} [:map
                               [:list-changed {:optional true} :boolean]]]
   [:resources {:optional true} [:map
                                 [:list-changed {:optional true} :boolean]
                                 [:subscribe {:optional true} :boolean]]]
   [:tools {:optional true} [:map
                             [:list-changed {:optional true} :boolean]]]])

;; A request that expects a response.
(def json-rpc-request
  [:map
   [:jsonrpc [:= "2.0"]]
   [:id request-id]
   [:method :string]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map
                                                        [:progress-token {:optional true} progress-token]]]]]])

;; A notification which does not expect a response.
(def json-rpc-notification
  [:map
   [:jsonrpc [:= "2.0"]]
   [:method :string]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map]]]]])

;; A successful (non-error) response to a request.
(def json-rpc-response
  [:map
   [:jsonrpc [:= "2.0"]]
   [:id request-id]
   [:result result]])

;; A response to a request that indicates an error occurred.
(def json-rpc-error
  [:map
   [:jsonrpc [:= "2.0"]]
   [:id request-id]
   [:error [:map
            [:code :int]
            [:message :string]
            [:data {:optional true} :any]]]])

;; This request is sent from the client to the server when it first connects, asking it to begin initialization.
(def initialize-request
  [:map
   [:method [:= "initialize"]]
   [:params [:map
             [:protocol-version :string]
             [:capabilities client-capabilities]
             [:client-info implementation]]]])

;; A ping, issued by either the server or the client, to check that the other party is still alive.
;; The receiver must promptly respond, or else may be disconnected.
(def ping-request
  [:map
   [:method [:= "ping"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map
                                                        [:progress-token {:optional true} progress-token]]]]]])

;; Sent from the client to request a list of resources the server has.
(def list-resources-request
  [:map
   [:method [:= "resources/list"]]
   [:params {:optional true} [:map
                              [:cursor {:optional true} cursor]]]])

;; Sent from the client to request a list of resource templates the server has.
(def list-resource-templates-request
  [:map
   [:method [:= "resources/templates/list"]]
   [:params {:optional true} [:map
                              [:cursor {:optional true} cursor]]]])

;; Sent from the client to the server, to read a specific resource URI.
(def read-resource-request
  [:map
   [:method [:= "resources/read"]]
   [:params [:map
             [:uri [:string {:format :uri}]]]]])

;; Sent from the client to request resources/updated notifications from the server whenever a particular resource changes.
(def subscribe-request
  [:map
   [:method [:= "resources/subscribe"]]
   [:params [:map
             [:uri [:string {:format :uri}]]]]])

;; Sent from the client to request cancellation of resources/updated notifications from the server.
;; This should follow a previous resources/subscribe request.
(def unsubscribe-request
  [:map
   [:method [:= "resources/unsubscribe"]]
   [:params [:map
             [:uri [:string {:format :uri}]]]]])

;; Sent from the client to request a list of prompts and prompt templates the server has.
(def list-prompts-request
  [:map
   [:method [:= "prompts/list"]]
   [:params {:optional true} [:map
                              [:cursor {:optional true} cursor]]]])

;; Used by the client to get a prompt provided by the server.
(def get-prompt-request
  [:map
   [:method [:= "prompts/get"]]
   [:params [:map
             [:name :string]
             [:arguments {:optional true} [:map-of :string :string]]]]])

;; Sent from the client to request a list of tools the server has.
(def list-tools-request
  [:map
   [:method [:= "tools/list"]]
   [:params {:optional true} [:map
                              [:cursor {:optional true} cursor]]]])

;; Used by the client to invoke a tool provided by the server.
(def call-tool-request
  [:map
   [:method [:= "tools/call"]]
   [:params [:map
             [:name :string]
             [:arguments {:optional true} [:map-of :string :any]]]]])

;; A request from the client to the server, to ask for completion options.
(def complete-request
  [:map
   [:method [:= "completion/complete"]]
   [:params [:map
             [:ref [:or prompt-reference resource-reference]]
             [:argument [:map
                         [:name :string]
                         [:value :string]]]]]])

;; A request from the client to the server, to enable or adjust logging.
(def set-level-request
  [:map
   [:method [:= "logging/setLevel"]]
   [:params [:map
             [:level logging-level]]]])

;; A request from the server to sample an LLM via the client. The client has full discretion over which model to select.
;; The client should also inform the user before beginning sampling, to allow them to inspect the request (human in the loop)
;; and decide whether to approve it.
(def create-message-request
  [:map
   [:method [:= "sampling/createMessage"]]
   [:params [:map
             [:max-tokens :int]
             [:messages [:vector sampling-message]]
             [:system-prompt {:optional true} :string]
             [:include-context {:optional true} [:enum "allServers" "none" "thisServer"]]
             [:temperature {:optional true} :double]
             [:stop-sequences {:optional true} [:vector :string]]
             [:metadata {:optional true} [:map]]
             [:model-preferences {:optional true} model-preferences]]]])

;; Sent from the server to request a list of root URIs from the client. Roots allow
;; servers to ask for specific directories or files to operate on. A common example
;; for roots is providing a set of repositories or directories a server should operate
;; on.
;; This request is typically used when the server needs to understand the file system
;; structure or access specific locations that the client has permission to read from.
(def list-roots-request
  [:map
   [:method [:= "roots/list"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map
                                                        [:progress-token {:optional true} progress-token]]]]]])

;; After receiving an initialize request from the client, the server sends this response.
(def initialize-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:protocol-version :string]
   [:capabilities server-capabilities]
   [:server-info implementation]
   [:instructions {:optional true} :string]])

;; The server's response to a resources/list request from the client.
(def list-resources-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:resources [:vector resource]]
   [:next-cursor {:optional true} cursor]])

;; The server's response to a resources/templates/list request from the client.
(def list-resource-templates-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:resource-templates [:vector resource-template]]
   [:next-cursor {:optional true} cursor]])

;; The server's response to a resources/read request from the client.
(def read-resource-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:contents [:vector [:or text-resource-contents blob-resource-contents]]]])

;; The server's response to a prompts/list request from the client.
(def list-prompts-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:prompts [:vector prompt]]
   [:next-cursor {:optional true} cursor]])

;; The server's response to a prompts/get request from the client.
(def get-prompt-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:description {:optional true} :string]
   [:messages [:vector prompt-message]]])

;; The server's response to a tools/list request from the client.
(def list-tools-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:tools [:vector tool]]
   [:next-cursor {:optional true} cursor]])

;; The server's response to a tool call.
;; Any errors that originate from the tool SHOULD be reported inside the result
;; object, with `isError` set to true, _not_ as an MCP protocol-level error
;; response. Otherwise, the LLM would not be able to see that an error occurred
;; and self-correct.
;; However, any errors in _finding_ the tool, an error indicating that the
;; server does not support tool calls, or any other exceptional conditions,
;; should be reported as an MCP error response.
(def call-tool-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:content [:vector content]]
   [:is-error {:optional true} :boolean]])

;; The server's response to a completion/complete request
(def complete-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:completion [:map
                 [:values [:vector :string]]
                 [:total {:optional true} :int]
                 [:has-more {:optional true} :boolean]]]])

;; The client's response to a sampling/create_message request from the server. The client should inform the user before
;; returning the sampled message, to allow them to inspect the response (human in the loop) and decide whether to allow
;; the server to see it.
(def create-message-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:role role]
   [:content [:or text-content image-content audio-content]]
   [:model :string]
   [:stop-reason {:optional true} :string]])

;; The client's response to a roots/list request from the server.
;; This result contains an array of Root objects, each representing a root directory
;; or file that the server can operate on.
(def list-roots-result
  [:map
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:roots [:vector root]]])

;; This notification can be sent by either side to indicate that it is cancelling a previously-issued request.
;; The request SHOULD still be in-flight, but due to communication latency, it is always possible that this notification
;; MAY arrive after the request has already finished.
;; This notification indicates that the result will be unused, so any associated processing SHOULD cease.
;; A client MUST NOT attempt to cancel its `initialize` request.
(def cancelled-notification
  [:map
   [:method [:= "notifications/cancelled"]]
   [:params [:map
             [:request-id request-id]
             [:reason {:optional true} :string]]]])

;; This notification is sent from the client to the server after initialization has finished.
(def initialized-notification
  [:map
   [:method [:= "notifications/initialized"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map]]]]])

;; An out-of-band notification used to inform the receiver of a progress update for a long-running request.
(def progress-notification
  [:map
   [:method [:= "notifications/progress"]]
   [:params [:map
             [:progress-token progress-token]
             [:progress :double]
             [:total {:optional true} :double]
             [:message {:optional true} :string]]]])

;; A notification from the client to the server, informing it that the list of roots has changed.
;; This notification should be sent whenever the client adds, removes, or modifies any root.
;; The server should then request an updated list of roots using the ListRootsRequest.
(def roots-list-changed-notification
  [:map
   [:method [:= "notifications/roots/list_changed"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map]]]]])

;; An optional notification from the server to the client, informing it that the list of resources it can read from has changed.
;; This may be issued by servers without any previous subscription from the client.
(def resource-list-changed-notification
  [:map
   [:method [:= "notifications/resources/list_changed"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map]]]]])

;; A notification from the server to the client, informing it that a resource has changed and may need to be read again.
;; This should only be sent if the client previously sent a resources/subscribe request.
(def resource-updated-notification
  [:map
   [:method [:= "notifications/resources/updated"]]
   [:params [:map
             [:uri [:string {:format :uri}]]]]])

;; An optional notification from the server to the client, informing it that the list of prompts it offers has changed.
;; This may be issued by servers without any previous subscription from the client.
(def prompt-list-changed-notification
  [:map
   [:method [:= "notifications/prompts/list_changed"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map]]]]])

;; An optional notification from the server to the client, informing it that the list of tools it offers has changed.
;; This may be issued by servers without any previous subscription from the client.
(def tool-list-changed-notification
  [:map
   [:method [:= "notifications/tools/list_changed"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map]]]]])

;; Notification of a log message passed from server to client. If no logging/setLevel request has been sent from the client,
;; the server MAY decide which messages to send automatically.
(def logging-message-notification
  [:map
   [:method [:= "notifications/message"]]
   [:params [:map
             [:level logging-level]
             [:data :any]
             [:logger {:optional true} :string]]]])

;; Union types
(def client-request
  [:or initialize-request ping-request list-resources-request
   list-resource-templates-request read-resource-request
   subscribe-request unsubscribe-request list-prompts-request
   get-prompt-request list-tools-request call-tool-request
   set-level-request complete-request])

(def server-request
  [:or ping-request create-message-request list-roots-request])

(def client-notification
  [:or cancelled-notification initialized-notification
   progress-notification roots-list-changed-notification])

(def server-notification
  [:or cancelled-notification progress-notification
   resource-list-changed-notification resource-updated-notification
   prompt-list-changed-notification tool-list-changed-notification
   logging-message-notification])

(def client-result
  [:or result create-message-result list-roots-result])

(def server-result
  [:or result initialize-result list-resources-result
   list-resource-templates-result read-resource-result
   list-prompts-result get-prompt-result list-tools-result
   call-tool-result complete-result])

;; Refers to any valid JSON-RPC object that can be decoded off the wire, or encoded to be sent.
(def json-rpc-message
  [:or json-rpc-request json-rpc-notification json-rpc-response json-rpc-error
   [:vector [:or json-rpc-request json-rpc-notification]]  ; batch request
   [:vector [:or json-rpc-response json-rpc-error]]])      ; batch response
