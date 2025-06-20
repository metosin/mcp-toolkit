(ns mcp-toolkit.client
  (:require [mate.core :as mc]
            [mcp-toolkit.json-rpc :as json-rpc]
            [mcp-toolkit.impl.client.handler :as client.handler]
            [promesa.core :as p]))

(defn- user-callback [callback-key]
  (fn [context]
    (when-some [callback (-> context :session deref (get callback-key))]
      (callback context))))

(defn request-set-logging-level [context level]
  (json-rpc/call-remote-method context {:method "logging/setLevel"
                                        :params {:level level}}))

(defn request-complete-prompt-param [context prompt-name
                                     param-name param-value]
  (json-rpc/call-remote-method context {:method "completion/complete"
                                        :params {:ref {:type "ref/prompt"
                                                       :name prompt-name}
                                                 :argument {:name param-name
                                                            :value param-value}}}))

(defn request-complete-resource-uri [context uri-template
                                     param-name param-value]
  (json-rpc/call-remote-method context {:method "completion/complete"
                                        :params {:ref {:type "ref/resource"
                                                       :uri uri-template}
                                                 :argument {:name param-name
                                                            :value param-value}}}))

(defn request-prompt-list [context]
  (let [{:keys [session]} context
        {:keys [server-capabilities]} @session]
    (when (contains? server-capabilities :prompts)
      (-> (json-rpc/call-remote-method context {:method "prompts/list"})
          (p/then (fn [{:keys [prompts]}]
                    (swap! session assoc :server-prompt-by-name (mc/index-by :name prompts))
                    ((user-callback :on-server-prompt-list-updated) context)))))))

(defn request-prompt [context prompt-name arguments]
  (json-rpc/call-remote-method context {:method "prompts/get"
                                        :params {:name prompt-name
                                                 :arguments arguments}}))

(defn request-resource-list [context]
  (let [{:keys [session]} context
        {:keys [server-capabilities]} @session]
    (when (contains? server-capabilities :resources)
      (-> (json-rpc/call-remote-method context {:method "resources/list"})
          (p/then (fn [{:keys [resources] :as result}]
                    (swap! session assoc :server-resource-by-uri (mc/index-by :uri resources))
                    ((user-callback :on-server-resource-list-updated) context)))))))

(defn request-resource [context resource-uri]
  (json-rpc/call-remote-method context {:method "resources/read"
                                        :params {:uri resource-uri}}))

(defn request-resource-template-list [context]
  (json-rpc/call-remote-method context {:method "resources/templates/list"}))


(defn request-subscribe-resource [context resource-uri]
  (json-rpc/call-remote-method context {:method "resources/subscribe"
                                        :params {:uri resource-uri}}))

(defn request-unsubscribe-resource [context resource-uri]
  (json-rpc/call-remote-method context {:method "resources/unsubscribe"
                                        :params {:uri resource-uri}}))

(defn request-tool-list [context]
  (let [{:keys [session]} context
        {:keys [server-capabilities on-server-tools-updated]} @session]
    (when (contains? server-capabilities :prompts)
      (-> (json-rpc/call-remote-method context {:method "tools/list"})
          (p/then (fn [{:keys [tools] :as result}]
                    (swap! session assoc :server-tool-by-name (mc/index-by :name tools))
                    (when (some? on-server-tools-updated)
                      (on-server-tools-updated context))))))))

(defn request-tool-invocation [context tool-name arguments]
  (json-rpc/call-remote-method context {:method "tools/call"
                                        :params {:name tool-name
                                                 :arguments arguments}}))

(defn notify-cancel-request [context request-id]
  (json-rpc/send-message context (json-rpc/notification "cancelled"
                                                        {:requestId request-id})))

(defn notify-root-list-updated [context]
  (json-rpc/send-message context (json-rpc/notification "roots/list_changed")))

(defn send-first-handshake-message [context]
  (let [{:keys [session]} context
        {:keys [client-info
                protocol-version]} @session]
    (-> (json-rpc/call-remote-method context {:method "initialize"
                                              :params {:clientInfo      client-info
                                                       :protocolVersion protocol-version
                                                       :capabilities    {:roots    {:listChanged true}
                                                                         #_#_
                                                                         :sampling {}}}})
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
           protocol-version
           roots
           on-initialized
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
                                            :version "0.0.1"}
           protocol-version                "2025-03-26"
           on-initialized                  default-on-initialized
           on-server-prompt-list-changed   request-prompt-list
           on-server-resource-list-changed request-resource-list
           on-server-tool-list-changed     request-tool-list}}]
  {:client-info                 client-info
   :protocol-version            protocol-version

   :initialized                 false
   :on-initialized              on-initialized
   :handler-by-method           client.handler/handler-by-method-pre-initialization

   :root-by-uri                 (mc/index-by :uri roots)

   :server-prompt-by-name       {}
   :server-resource-by-uri      {}
   :server-tool-by-name         {}
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
