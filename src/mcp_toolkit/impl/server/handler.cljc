(ns mcp-toolkit.impl.server.handler
  (:require [mcp-toolkit.json-rpc.message :as json-rpc.message]
            [promesa.core :as p]))

(defn- user-callback [callback-key]
  (fn [context]
    (when-some [callback (-> context :session deref (get callback-key))]
      (callback context))))

(defn ping-handler [context]
  {})

(defn set-logging-level-handler [{:keys [session message]}]
  (let [logging-level (-> message :params :level)]
    (swap! session assoc :logging-level logging-level))
  {})

(defn completion-complete-handler [{:keys [session message] :as context}]
  (let [{:keys [ref argument]} (:params message)]
    (case (:type ref)
      "ref/prompt" (if-some [complete-fn (-> @session :prompt-by-name (get (:name ref)) :complete-fn)]
                     (complete-fn context (:name argument) (:value argument))
                     (json-rpc.message/method-not-found-response (:id message)))
      "ref/resource" (if-some [complete-fn (:resource-uri-complete-fn @session)]
                       (complete-fn context (:uri ref) (:name argument) (:value argument))
                       (json-rpc.message/method-not-found-response (:id message))))))

(defn prompt-list-handler [{:keys [session]}]
  {:prompts (-> @session :prompt-by-name vals
                (->> (mapv (fn [prompt]
                             (select-keys prompt [:name :description :arguments])))))
   #_#_
   :nextCursor "next-page-cursor"})

(defn prompt-get-handler [{:keys [session message] :as context}]
  (let [{:keys [name arguments]} (:params message)]
    (if-some [prompt-fn (-> @session :prompt-by-name (get name) :prompt-fn)]
      (prompt-fn context arguments)
      (json-rpc.message/method-not-found-response (:id message)))))

(defn resource-list-handler [{:keys [session]}]
  {:resources (-> @session :resource-by-uri vals
                  (->> (mapv (fn [resource]
                               (select-keys resource [:uri :name :description :mimeType])))))
   #_#_
   :nextCursor "next-page-cursor"})

(defn resource-read-handler [{:keys [session message]}]
  (let [{:keys [uri]} (:params message)]
    (if-some [resource (-> @session :resource-by-uri (get uri))]
      {:contents [(select-keys resource [:uri :description :mimeType :text :blob])]} ; either text or blob
      ;; FIXME: this is wrong because it will be interpreted as result data
      (json-rpc.message/resource-not-found (:id message) uri))))

(defn resource-templates-list-handler [{:keys [session]}]
  {:resourceTemplates (-> @session (:resource-templates []))})

(defn resource-subscribe-handler [{:keys [session message]}]
  (let [{:keys [uri]} (:params message)]
    (swap! session update :client-subscribed-resource-uris conj uri))
  {})

(defn resource-unsubscribe-handler [{:keys [session message]}]
  (let [{:keys [uri]} (:params message)]
    (swap! session update :client-subscribed-resource-uris disj uri))
  {})

(defn tool-list-handler [{:keys [session]}]
  {:tools (-> @session :tool-by-name vals
              (->> (mapv (fn [tool]
                           (select-keys tool [:name :description :inputSchema])))))
   #_#_
   :nextCursor "next-page-cursor"})

(defn tool-call-handler [{:keys [session message] :as context}]
  (let [{:keys [name arguments]} (:params message)]
    (if-some [tool-fn (-> @session :tool-by-name (get name) :tool-fn)]
      (-> (tool-fn context arguments)
          (p/catch (fn [exception]
                     {:content [{:type "text"
                                 :text (ex-message exception)}]
                      :isError true})))
      ;; FIXME: this is wrong because it will be interpreted as result data
      (json-rpc.message/invalid-tool-name (:id message) name))))

(defn cancelled-notification-handler [{:keys [session message]}]
  (when-some [is-cancelled-atom (-> @session :is-cancelled-by-request-id (get (-> message :params :requestId)))]
    (reset! is-cancelled-atom true)))

(def handler-by-method-post-initialization
  {"ping"                             ping-handler
   "logging/setLevel"                 set-logging-level-handler
   "completion/complete"              completion-complete-handler
   "prompts/list"                     prompt-list-handler
   "prompts/get"                      prompt-get-handler
   "resources/list"                   resource-list-handler
   "resources/read"                   resource-read-handler
   "resources/templates/list"         resource-templates-list-handler
   "resources/subscribe"              resource-subscribe-handler
   "resources/unsubscribe"            resource-unsubscribe-handler
   "tools/list"                       tool-list-handler
   "tools/call"                       tool-call-handler
   "notifications/cancelled"          cancelled-notification-handler
   "notifications/roots/list_changed" (user-callback :on-client-root-list-changed)})


;; Initialization phase, a handshake where protocol versions are tentatively agreed.

(defn initialize-handler [{:keys [session message]}]
  (let [{client-protocol-version :protocolVersion
         client-info             :clientInfo
         client-capabilities     :capabilities} (:params message)
        {:keys [server-info
                server-supported-protocol-versions
                server-instructions]} @session
        protocol-version (if (contains? (set server-supported-protocol-versions) client-protocol-version)
                           client-protocol-version
                           (last server-supported-protocol-versions))]
    (swap! session assoc
           :protocol-version protocol-version
           :client-info client-info
           :client-capabilities client-capabilities)
    (-> {:protocolVersion protocol-version
         :capabilities {:logging     {}
                        :completions {}
                        :prompts     {:listChanged true}
                        :resources   {:subscribe true
                                      :listChanged true}
                        :tools       {:listChanged true}}
         :serverInfo server-info}
        (cond-> (some? server-instructions) (assoc :instructions server-instructions)))))

(defn initialized-notification-handler [{:keys [session] :as context}]
  (swap! session assoc
         :initialized true
         :handler-by-method handler-by-method-post-initialization)
  ((user-callback :on-initialized) context))

(def handler-by-method-pre-initialization
  {"ping"                      ping-handler
   "initialize"                initialize-handler
   "notifications/initialized" initialized-notification-handler})
