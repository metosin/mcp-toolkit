(ns mcp-toolkit.schema.mcp-2024-11-05
  (:require [malli.generator :as mg]))

;; Model Context Protocol (MCP) Schema converted to Malli
;; Based on: https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/refs/heads/main/schema/2024-11-05/schema.json

;; The sender or recipient of messages and data in a conversation.
(def role
  [:enum "assistant" "user"])

;; The severity of a log message.
;;
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

;; Base for objects that include optional annotations for the client. The client can use annotations to inform how objects are used or displayed
(def annotated
  [:map
   [:annotations {:optional true} [:map
                                   ;; Describes who the intended customer of this object or data is.
                                   ;;
                                   ;; It can include multiple entries to indicate content useful for multiple audiences (e.g., `["user", "assistant"]`).
                                   [:audience {:optional true} [:vector role]]

                                   ;; Describes how important this data is for operating the server.
                                   ;;
                                   ;; A value of 1 means "most important," and indicates that the data is
                                   ;; effectively required, while 0 means "least important," and indicates that
                                   ;; the data is entirely optional.
                                   [:priority {:optional true} [:double {:min 0 :max 1}]]]]])

;; Text provided to or from an LLM.
(def text-content
  [:map
   [:type [:= "text"]]
   ;; The text content of the message.
   [:text :string]
   [:annotations {:optional true} [:map
                                   ;; Describes who the intended customer of this object or data is.
                                   ;;
                                   ;; It can include multiple entries to indicate content useful for multiple audiences (e.g., `["user", "assistant"]`).
                                   [:audience {:optional true} [:vector role]]

                                   ;; Describes how important this data is for operating the server.
                                   ;;
                                   ;; A value of 1 means "most important," and indicates that the data is
                                   ;; effectively required, while 0 means "least important," and indicates that
                                   ;; the data is entirely optional.
                                   [:priority {:optional true} [:double {:min 0 :max 1}]]]]])

;; An image provided to or from an LLM.
(def image-content
  [:map
   [:type [:= "image"]]
   ;; The base64-encoded image data.
   [:data [:string {:description "Base64-encoded image data"}]]
   ;; The MIME type of the image. Different providers may support different image types.
   [:mimeType :string]
   [:annotations {:optional true} [:map
                                   ;; Describes who the intended customer of this object or data is.
                                   ;;
                                   ;; It can include multiple entries to indicate content useful for multiple audiences (e.g., `["user", "assistant"]`).
                                   [:audience {:optional true} [:vector role]]

                                   ;; Describes how important this data is for operating the server.
                                   ;;
                                   ;; A value of 1 means "most important," and indicates that the data is
                                   ;; effectively required, while 0 means "least important," and indicates that
                                   ;; the data is entirely optional.
                                   [:priority {:optional true} [:double {:min 0 :max 1}]]]]])

(def text-resource-contents
  [:map
   ;; The URI of this resource.
   [:uri [:string {:format :uri}]]
   ;; The text of the item. This must only be set if the item can actually be represented as text (not binary data).
   [:text :string]
   ;; The MIME type of this resource, if known.
   [:mimeType {:optional true} :string]])

(def blob-resource-contents
  [:map
   ;; The URI of this resource.
   [:uri [:string {:format :uri}]]
   ;; A base64-encoded string representing the binary data of the item.
   [:blob [:string {:description "Base64-encoded binary data"}]]
   ;; The MIME type of this resource, if known.
   [:mimeType {:optional true} :string]])

;; The contents of a resource, embedded into a prompt or tool call result.
;;
;; It is up to the client how best to render embedded resources for the benefit
;; of the LLM and/or the user.
(def embedded-resource
  [:map
   [:type [:= "resource"]]
   [:resource [:or text-resource-contents blob-resource-contents]]
   [:annotations {:optional true} [:map
                                   ;; Describes who the intended customer of this object or data is.
                                   ;;
                                   ;; It can include multiple entries to indicate content useful for multiple audiences (e.g., `["user", "assistant"]`).
                                   [:audience {:optional true} [:vector role]]

                                   ;; Describes how important this data is for operating the server.
                                   ;;
                                   ;; A value of 1 means "most important," and indicates that the data is
                                   ;; effectively required, while 0 means "least important," and indicates that
                                   ;; the data is entirely optional.
                                   [:priority {:optional true} [:double {:min 0 :max 1}]]]]])

(def content
  [:or text-content image-content embedded-resource])

;; Describes the name and version of an MCP implementation.
(def implementation
  [:map
   [:name :string]
   [:version :string]])

(def result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]])

;; Hints to use for model selection.
;;
;; Keys not declared here are currently left unspecified by the spec and are up
;; to the client to interpret.
(def model-hint
  [:map
   ;; A hint for a model name.
   ;;
   ;; The client SHOULD treat this as a substring of a model name; for example:
   ;;  - `claude-3-5-sonnet` should match `claude-3-5-sonnet-20241022`
   ;;  - `sonnet` should match `claude-3-5-sonnet-20241022`, `claude-3-sonnet-20240229`, etc.
   ;;  - `claude` should match any Claude model
   ;;
   ;; The client MAY also map the string to a different provider's model name or a different model family, as long as it fills a similar niche; for example:
   ;;  - `gemini-1.5-flash` could match `claude-3-haiku-20240307`
   [:name {:optional true} :string]])

;; The server's preferences for model selection, requested of the client during sampling.
;;
;; Because LLMs can vary along multiple dimensions, choosing the "best" model is
;; rarely straightforward.  Different models excel in different areas—some are
;; faster but less capable, others are more capable but more expensive, and so
;; on. This interface allows servers to express their priorities across multiple
;; dimensions to help clients make an appropriate selection for their use case.
;;
;; These preferences are always advisory. The client MAY ignore them. It is also
;; up to the client to decide how to interpret these preferences and how to
;; balance them against other considerations.
(def model-preferences
  [:map
   ;; How much to prioritize cost when selecting a model. A value of 0 means cost
   ;; is not important, while a value of 1 means cost is the most important
   ;; factor.
   [:costPriority {:optional true} [:double {:min 0 :max 1}]]

   ;; How much to prioritize intelligence and capabilities when selecting a
   ;; model. A value of 0 means intelligence is not important, while a value of 1
   ;; means intelligence is the most important factor.
   [:intelligencePriority {:optional true} [:double {:min 0 :max 1}]]

   ;; How much to prioritize sampling speed (latency) when selecting a model. A
   ;; value of 0 means speed is not important, while a value of 1 means speed is
   ;; the most important factor.
   [:speedPriority {:optional true} [:double {:min 0 :max 1}]]

   ;; Optional hints to use for model selection.
   ;;
   ;; If multiple hints are specified, the client MUST evaluate them in order
   ;; (such that the first match is taken).
   ;;
   ;; The client SHOULD prioritize these hints over the numeric priorities, but
   ;; MAY still use the priorities to select from ambiguous matches.
   [:hints {:optional true} [:vector model-hint]]])

;; Describes a message issued to or received from an LLM API.
(def sampling-message
  [:map
   [:role role]
   [:content [:or text-content image-content]]])

;; Describes a message returned as part of a prompt.
;;
;; This is similar to `SamplingMessage`, but also supports the embedding of
;; resources from the MCP server.
(def prompt-message
  [:map
   [:role role]
   [:content [:or text-content image-content embedded-resource]]])

;; Describes an argument that a prompt can accept.
(def prompt-argument
  [:map
   ;; The name of the argument.
   [:name :string]
   ;; A human-readable description of the argument.
   [:description {:optional true} :string]
   ;; Whether this argument must be provided.
   [:required {:optional true} :boolean]])

;; A prompt or prompt template that the server offers.
(def prompt
  [:map
   ;; The name of the prompt or prompt template.
   [:name :string]
   ;; An optional description of what this prompt provides
   [:description {:optional true} :string]
   ;; A list of arguments to use for templating the prompt.
   [:arguments {:optional true} [:vector prompt-argument]]])

;; Identifies a prompt.
(def prompt-reference
  [:map
   [:type [:= "ref/prompt"]]
   ;; The name of the prompt or prompt template
   [:name :string]])

;; A reference to a resource or resource template definition.
(def resource-reference
  [:map
   [:type [:= "ref/resource"]]
   ;; The URI or URI template of the resource.
   [:uri [:string {:format :uri-template}]]])

;; The contents of a specific resource or sub-resource.
(def resource-contents
  [:map
   ;; The URI of this resource.
   [:uri [:string {:format :uri}]]
   ;; The MIME type of this resource, if known.
   [:mimeType {:optional true} :string]])

;; A known resource that the server is capable of reading.
(def resource
  [:map
   ;; A human-readable name for this resource.
   ;;
   ;; This can be used by clients to populate UI elements.
   [:name :string]

   ;; The URI of this resource.
   [:uri [:string {:format :uri}]]

   ;; A description of what this resource represents.
   ;;
   ;; This can be used by clients to improve the LLM's understanding of available resources. It can be thought of like a "hint" to the model.
   [:description {:optional true} :string]

   ;; The MIME type of this resource, if known.
   [:mimeType {:optional true} :string]

   ;; The size of the raw resource content, in bytes (i.e., before base64 encoding or any tokenization), if known.
   ;;
   ;; This can be used by Hosts to display file sizes and estimate context window usage.
   [:size {:optional true} :int]

   [:annotations {:optional true} [:map
                                   ;; Describes who the intended customer of this object or data is.
                                   ;;
                                   ;; It can include multiple entries to indicate content useful for multiple audiences (e.g., `["user", "assistant"]`).
                                   [:audience {:optional true} [:vector role]]

                                   ;; Describes how important this data is for operating the server.
                                   ;;
                                   ;; A value of 1 means "most important," and indicates that the data is
                                   ;; effectively required, while 0 means "least important," and indicates that
                                   ;; the data is entirely optional.
                                   [:priority {:optional true} [:double {:min 0 :max 1}]]]]])

;; A template description for resources available on the server.
(def resource-template
  [:map
   ;; A human-readable name for the type of resource this template refers to.
   ;;
   ;; This can be used by clients to populate UI elements.
   [:name :string]

   ;; A URI template (according to RFC 6570) that can be used to construct resource URIs.
   [:uriTemplate [:string {:format :uri-template}]]

   ;; A description of what this template is for.
   ;;
   ;; This can be used by clients to improve the LLM's understanding of available resources. It can be thought of like a "hint" to the model.
   [:description {:optional true} :string]

   ;; The MIME type for all resources that match this template. This should only be included if all resources matching this template have the same type.
   [:mimeType {:optional true} :string]

   [:annotations {:optional true} [:map
                                   ;; Describes who the intended customer of this object or data is.
                                   ;;
                                   ;; It can include multiple entries to indicate content useful for multiple audiences (e.g., `["user", "assistant"]`).
                                   [:audience {:optional true} [:vector role]]

                                   ;; Describes how important this data is for operating the server.
                                   ;;
                                   ;; A value of 1 means "most important," and indicates that the data is
                                   ;; effectively required, while 0 means "least important," and indicates that
                                   ;; the data is entirely optional.
                                   [:priority {:optional true} [:double {:min 0 :max 1}]]]]])

;; Definition for a tool the client can call.
(def tool
  [:map
   ;; The name of the tool.
   [:name :string]

   ;; A human-readable description of the tool.
   [:description {:optional true} :string]

   ;; A JSON Schema object defining the expected parameters for the tool.
   [:inputSchema [:map
                  [:type [:= "object"]]
                  [:properties {:optional true} [:map-of :string :any]]
                  [:required {:optional true} [:vector :string]]]]])

;; Represents a root directory or file that the server can operate on.
(def root
  [:map
   ;; The URI identifying the root. This *must* start with file:// for now.
   ;; This restriction may be relaxed in future versions of the protocol to allow
   ;; other URI schemes.
   [:uri [:string {:format :uri}]]

   ;; An optional name for the root. This can be used to provide a human-readable
   ;; identifier for the root, which may be useful for display purposes or for
   ;; referencing the root in other parts of the application.
   [:name {:optional true} :string]])

;; Capabilities a client may support. Known capabilities are defined here, in this schema, but this is not a closed set: any client can define its own, additional capabilities.
(def client-capabilities
  [:map
   ;; Experimental, non-standard capabilities that the client supports.
   [:experimental {:optional true} [:map-of :string :any]]

   ;; Present if the client supports listing roots.
   [:roots {:optional true} [:map
                             ;; Whether the client supports notifications for changes to the roots list.
                             [:listChanged {:optional true} :boolean]]]

   ;; Present if the client supports sampling from an LLM.
   [:sampling {:optional true} [:map]]])

;; Capabilities that a server may support. Known capabilities are defined here, in this schema, but this is not a closed set: any server can define its own, additional capabilities.
(def server-capabilities
  [:map
   ;; Experimental, non-standard capabilities that the server supports.
   [:experimental {:optional true} [:map-of :string :any]]

   ;; Present if the server supports sending log messages to the client.
   [:logging {:optional true} [:map]]

   ;; Present if the server offers any prompt templates.
   [:prompts {:optional true} [:map
                               ;; Whether this server supports notifications for changes to the prompt list.
                               [:listChanged {:optional true} :boolean]]]

   ;; Present if the server offers any resources to read.
   [:resources {:optional true} [:map
                                 ;; Whether this server supports notifications for changes to the resource list.
                                 [:listChanged {:optional true} :boolean]
                                 ;; Whether this server supports subscribing to resource updates.
                                 [:subscribe {:optional true} :boolean]]]

   ;; Present if the server offers any tools to call.
   [:tools {:optional true} [:map
                             ;; Whether this server supports notifications for changes to the tool list.
                             [:listChanged {:optional true} :boolean]]]])

;; A request that expects a response.
(def json-rpc-request
  [:map
   [:jsonrpc [:= "2.0"]]
   [:id request-id]
   [:method :string]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map
                                                         ;; If specified, the caller is requesting out-of-band progress notifications for this request (as represented by notifications/progress). The value of this parameter is an opaque token that will be attached to any subsequent notifications. The receiver is not obligated to provide these notifications.
                                                         [:progressToken {:optional true} progress-token]]]]]])

;; A notification which does not expect a response.
(def json-rpc-notification
  [:map
   [:jsonrpc [:= "2.0"]]
   [:method :string]
   [:params {:optional true} [:map
                              ;; This parameter name is reserved by MCP to allow clients and servers to attach additional metadata to their notifications.
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
            ;; The error type that occurred.
            [:code :int]
            ;; A short description of the error. The message SHOULD be limited to a concise single sentence.
            [:message :string]
            ;; Additional information about the error. The value of this member is defined by the sender (e.g. detailed error information, nested errors etc.).
            [:data {:optional true} :any]]]])

(def notification
  [:map
   [:method :string]
   [:params {:optional true} [:map
                              ;; This parameter name is reserved by MCP to allow clients and servers to attach additional metadata to their notifications.
                              [:_meta {:optional true} [:map]]]]])

(def paginated-request
  [:map
   [:method :string]
   [:params {:optional true} [:map
                              ;; An opaque token representing the current pagination position.
                              ;; If provided, the server should return results starting after this cursor.
                              [:cursor {:optional true} cursor]]]])

(def paginated-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   ;; An opaque token representing the pagination position after the last returned result.
   ;; If present, there may be more results available.
   [:nextCursor {:optional true} cursor]])

(def request
  [:map
   [:method :string]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map
                                                         ;; If specified, the caller is requesting out-of-band progress notifications for this request (as represented by notifications/progress). The value of this parameter is an opaque token that will be attached to any subsequent notifications. The receiver is not obligated to provide these notifications.
                                                         [:progressToken {:optional true} progress-token]]]]]])

;; This request is sent from the client to the server when it first connects, asking it to begin initialization.
(def initialize-request
  [:map
   [:method [:= "initialize"]]
   [:params [:map
             ;; The latest version of the Model Context Protocol that the client supports. The client MAY decide to support older versions as well.
             [:protocolVersion :string]
             [:capabilities client-capabilities]
             [:clientInfo implementation]]]])

;; A ping, issued by either the server or the client, to check that the other party is still alive. The receiver must promptly respond, or else may be disconnected.
(def ping-request
  [:map
   [:method [:= "ping"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map
                                                         ;; If specified, the caller is requesting out-of-band progress notifications for this request (as represented by notifications/progress). The value of this parameter is an opaque token that will be attached to any subsequent notifications. The receiver is not obligated to provide these notifications.
                                                         [:progressToken {:optional true} progress-token]]]]]])

;; Sent from the client to request a list of resources the server has.
(def list-resources-request
  [:map
   [:method [:= "resources/list"]]
   [:params {:optional true} [:map
                              ;; An opaque token representing the current pagination position.
                              ;; If provided, the server should return results starting after this cursor.
                              [:cursor {:optional true} cursor]]]])

;; Sent from the client to request a list of resource templates the server has.
(def list-resource-templates-request
  [:map
   [:method [:= "resources/templates/list"]]
   [:params {:optional true} [:map
                              ;; An opaque token representing the current pagination position.
                              ;; If provided, the server should return results starting after this cursor.
                              [:cursor {:optional true} cursor]]]])

;; Sent from the client to the server, to read a specific resource URI.
(def read-resource-request
  [:map
   [:method [:= "resources/read"]]
   [:params [:map
             ;; The URI of the resource to read. The URI can use any protocol; it is up to the server how to interpret it.
             [:uri [:string {:format :uri}]]]]])

;; Sent from the client to request resources/updated notifications from the server whenever a particular resource changes.
(def subscribe-request
  [:map
   [:method [:= "resources/subscribe"]]
   [:params [:map
             ;; The URI of the resource to subscribe to. The URI can use any protocol; it is up to the server how to interpret it.
             [:uri [:string {:format :uri}]]]]])

;; Sent from the client to request cancellation of resources/updated notifications from the server. This should follow a previous resources/subscribe request.
(def unsubscribe-request
  [:map
   [:method [:= "resources/unsubscribe"]]
   [:params [:map
             ;; The URI of the resource to unsubscribe from.
             [:uri [:string {:format :uri}]]]]])

;; Sent from the client to request a list of prompts and prompt templates the server has.
(def list-prompts-request
  [:map
   [:method [:= "prompts/list"]]
   [:params {:optional true} [:map
                              ;; An opaque token representing the current pagination position.
                              ;; If provided, the server should return results starting after this cursor.
                              [:cursor {:optional true} cursor]]]])

;; Used by the client to get a prompt provided by the server.
(def get-prompt-request
  [:map
   [:method [:= "prompts/get"]]
   [:params [:map
             ;; The name of the prompt or prompt template.
             [:name :string]
             ;; Arguments to use for templating the prompt.
             [:arguments {:optional true} [:map-of :string :string]]]]])

;; Sent from the client to request a list of tools the server has.
(def list-tools-request
  [:map
   [:method [:= "tools/list"]]
   [:params {:optional true} [:map
                              ;; An opaque token representing the current pagination position.
                              ;; If provided, the server should return results starting after this cursor.
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
             ;; The argument's information
             [:argument [:map
                         ;; The name of the argument
                         [:name :string]
                         ;; The value of the argument to use for completion matching.
                         [:value :string]]]]]])

;; A request from the client to the server, to enable or adjust logging.
(def set-level-request
  [:map
   [:method [:= "logging/setLevel"]]
   [:params [:map
             ;; The level of logging that the client wants to receive from the server. The server should send all logs at this level and higher (i.e., more severe) to the client as notifications/message.
             [:level logging-level]]]])

;; A request from the server to sample an LLM via the client. The client has full discretion over which model to select. The client should also inform the user before beginning sampling, to allow them to inspect the request (human in the loop) and decide whether to approve it.
(def create-message-request
  [:map
   [:method [:= "sampling/createMessage"]]
   [:params [:map
             ;; The maximum number of tokens to sample, as requested by the server. The client MAY choose to sample fewer tokens than requested.
             [:maxTokens :int]
             [:messages [:vector sampling-message]]
             ;; An optional system prompt the server wants to use for sampling. The client MAY modify or omit this prompt.
             [:systemPrompt {:optional true} :string]
             ;; A request to include context from one or more MCP servers (including the caller), to be attached to the prompt. The client MAY ignore this request.
             [:includeContext {:optional true} [:enum "allServers" "none" "thisServer"]]
             [:temperature {:optional true} :double]
             [:stopSequences {:optional true} [:vector :string]]
             ;; Optional metadata to pass through to the LLM provider. The format of this metadata is provider-specific.
             [:metadata {:optional true} [:map]]
             ;; The server's preferences for which model to select. The client MAY ignore these preferences.
             [:modelPreferences {:optional true} model-preferences]]]])

;; Sent from the server to request a list of root URIs from the client. Roots allow
;; servers to ask for specific directories or files to operate on. A common example
;; for roots is providing a set of repositories or directories a server should operate
;; on.
;;
;; This request is typically used when the server needs to understand the file system
;; structure or access specific locations that the client has permission to read from.
(def list-roots-request
  [:map
   [:method [:= "roots/list"]]
   [:params {:optional true} [:map
                              [:_meta {:optional true} [:map
                                                         ;; If specified, the caller is requesting out-of-band progress notifications for this request (as represented by notifications/progress). The value of this parameter is an opaque token that will be attached to any subsequent notifications. The receiver is not obligated to provide these notifications.
                                                         [:progressToken {:optional true} progress-token]]]]]])

;; After receiving an initialize request from the client, the server sends this response.
(def initialize-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   ;; The version of the Model Context Protocol that the server wants to use. This may not match the version that the client requested. If the client cannot support this version, it MUST disconnect.
   [:protocolVersion :string]
   [:capabilities server-capabilities]
   [:serverInfo implementation]
   ;; Instructions describing how to use the server and its features.
   ;;
   ;; This can be used by clients to improve the LLM's understanding of available tools, resources, etc. It can be thought of like a "hint" to the model. For example, this information MAY be added to the system prompt.
   [:instructions {:optional true} :string]])

;; The server's response to a resources/list request from the client.
(def list-resources-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:resources [:vector resource]]
   ;; An opaque token representing the pagination position after the last returned result.
   ;; If present, there may be more results available.
   [:nextCursor {:optional true} cursor]])

;; The server's response to a resources/templates/list request from the client.
(def list-resource-templates-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:resourceTemplates [:vector resource-template]]
   ;; An opaque token representing the pagination position after the last returned result.
   ;; If present, there may be more results available.
   [:nextCursor {:optional true} cursor]])

;; The server's response to a resources/read request from the client.
(def read-resource-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:contents [:vector [:or text-resource-contents blob-resource-contents]]]])

;; The server's response to a prompts/list request from the client.
(def list-prompts-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:prompts [:vector prompt]]
   ;; An opaque token representing the pagination position after the last returned result.
   ;; If present, there may be more results available.
   [:nextCursor {:optional true} cursor]])

;; The server's response to a prompts/get request from the client.
(def get-prompt-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   ;; An optional description for the prompt.
   [:description {:optional true} :string]
   [:messages [:vector prompt-message]]])

;; The server's response to a tools/list request from the client.
(def list-tools-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:tools [:vector tool]]
   ;; An opaque token representing the pagination position after the last returned result.
   ;; If present, there may be more results available.
   [:nextCursor {:optional true} cursor]])

;; The server's response to a tool call.
;;
;; Any errors that originate from the tool SHOULD be reported inside the result
;; object, with `isError` set to true, _not_ as an MCP protocol-level error
;; response. Otherwise, the LLM would not be able to see that an error occurred
;; and self-correct.
;;
;; However, any errors in _finding_ the tool, an error indicating that the
;; server does not support tool calls, or any other exceptional conditions,
;; should be reported as an MCP error response.
(def call-tool-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:content [:vector [:or text-content image-content embedded-resource]]]
   ;; Whether the tool call ended in an error.
   ;;
   ;; If not set, this is assumed to be false (the call was successful).
   [:isError {:optional true} :boolean]])

;; The server's response to a completion/complete request
(def complete-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:completion [:map
                 ;; An array of completion values. Must not exceed 100 items.
                 [:values [:vector :string]]
                 ;; The total number of completion options available. This can exceed the number of values actually sent in the response.
                 [:total {:optional true} :int]
                 ;; Indicates whether there are additional completion options beyond those provided in the current response, even if the exact total is unknown.
                 [:hasMore {:optional true} :boolean]]]])

;; The client's response to a sampling/create_message request from the server. The client should inform the user before returning the sampled message, to allow them to inspect the response (human in the loop) and decide whether to allow the server to see it.
(def create-message-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:role role]
   [:content [:or text-content image-content]]
   ;; The name of the model that generated the message.
   [:model :string]
   ;; The reason why sampling stopped, if known.
   [:stopReason {:optional true} :string]])

;; The client's response to a roots/list request from the server.
;; This result contains an array of Root objects, each representing a root directory
;; or file that the server can operate on.
(def list-roots-result
  [:map
   ;; This result property is reserved by the protocol to allow clients and servers to attach additional metadata to their responses.
   [:_meta {:optional true} [:map-of :keyword :any]]
   [:roots [:vector root]]])

(def empty-result result)

;; This notification can be sent by either side to indicate that it is cancelling a previously-issued request.
;;
;; The request SHOULD still be in-flight, but due to communication latency, it is always possible that this notification MAY arrive after the request has already finished.
;;
;; This notification indicates that the result will be unused, so any associated processing SHOULD cease.
;;
;; A client MUST NOT attempt to cancel its `initialize` request.
(def cancelled-notification
  [:map
   [:method [:= "notifications/cancelled"]]
   [:params [:map
             ;; The ID of the request to cancel.
             ;;
             ;; This MUST correspond to the ID of a request previously issued in the same direction.
             [:requestId request-id]
             ;; An optional string describing the reason for the cancellation. This MAY be logged or presented to the user.
             [:reason {:optional true} :string]]]])

;; This notification is sent from the client to the server after initialization has finished.
(def initialized-notification
  [:map
   [:method [:= "notifications/initialized"]]
   [:params {:optional true} [:map
                              ;; This parameter name is reserved by MCP to allow clients and servers to attach additional metadata to their notifications.
                              [:_meta {:optional true} [:map]]]]])

;; An out-of-band notification used to inform the receiver of a progress update for a long-running request.
(def progress-notification
  [:map
   [:method [:= "notifications/progress"]]
   [:params [:map
             ;; The progress token which was given in the initial request, used to associate this notification with the request that is proceeding.
             [:progressToken progress-token]
             ;; The progress thus far. This should increase every time progress is made, even if the total is unknown.
             [:progress :double]
             ;; Total number of items to process (or total progress required), if known.
             [:total {:optional true} :double]]]])

;; A notification from the client to the server, informing it that the list of roots has changed.
;; This notification should be sent whenever the client adds, removes, or modifies any root.
;; The server should then request an updated list of roots using the ListRootsRequest.
(def roots-list-changed-notification
  [:map
   [:method [:= "notifications/roots/list_changed"]]
   [:params {:optional true} [:map
                              ;; This parameter name is reserved by MCP to allow clients and servers to attach additional metadata to their notifications.
                              [:_meta {:optional true} [:map]]]]])

;; An optional notification from the server to the client, informing it that the list of resources it can read from has changed. This may be issued by servers without any previous subscription from the client.
(def resource-list-changed-notification
  [:map
   [:method [:= "notifications/resources/list_changed"]]
   [:params {:optional true} [:map
                              ;; This parameter name is reserved by MCP to allow clients and servers to attach additional metadata to their notifications.
                              [:_meta {:optional true} [:map]]]]])

;; A notification from the server to the client, informing it that a resource has changed and may need to be read again. This should only be sent if the client previously sent a resources/subscribe request.
(def resource-updated-notification
  [:map
   [:method [:= "notifications/resources/updated"]]
   [:params [:map
             ;; The URI of the resource that has been updated. This might be a sub-resource of the one that the client actually subscribed to.
             [:uri [:string {:format :uri}]]]]])

;; An optional notification from the server to the client, informing it that the list of prompts it offers has changed. This may be issued by servers without any previous subscription from the client.
(def prompt-list-changed-notification
  [:map
   [:method [:= "notifications/prompts/list_changed"]]
   [:params {:optional true} [:map
                              ;; This parameter name is reserved by MCP to allow clients and servers to attach additional metadata to their notifications.
                              [:_meta {:optional true} [:map]]]]])

;; An optional notification from the server to the client, informing it that the list of tools it offers has changed. This may be issued by servers without any previous subscription from the client.
(def tool-list-changed-notification
  [:map
   [:method [:= "notifications/tools/list_changed"]]
   [:params {:optional true} [:map
                              ;; This parameter name is reserved by MCP to allow clients and servers to attach additional metadata to their notifications.
                              [:_meta {:optional true} [:map]]]]])

;; Notification of a log message passed from server to client. If no logging/setLevel request has been sent from the client, the server MAY decide which messages to send automatically.
(def logging-message-notification
  [:map
   [:method [:= "notifications/message"]]
   [:params [:map
             ;; The severity of this log message.
             [:level logging-level]
             ;; The data to be logged, such as a string message or an object. Any JSON serializable type is allowed here.
             [:data :any]
             ;; An optional name of the logger issuing this message.
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

(def json-rpc-message
  [:or json-rpc-request json-rpc-notification json-rpc-response json-rpc-error])
