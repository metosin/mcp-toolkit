(ns mcp-toolkit.server.handler
  (:require [mate.core :as mc]
            [mcp-toolkit.server.json-rpc-message :as json-rpc]
            [promesa.core :as p]))

(defn call-remote-method
  "Returns a promise which either
   resolves with the message's result or
   rejects with the message's error."
  [context {:keys [method params] :as message}]
  (let [{:keys [session send-message]} context
        ;; Pick a unique method id for a client call. Robust to concurrent calls.
        ;; TODO: ensure it loops when reaching the maximum integer value.
        client-method-id (-> (swap! session update :last-used-client-method-id inc)
                             :last-used-client-method-id)]
    (p/create
      (fn [resolve reject]
        (let [response-handler (fn [{:keys [session message]}]
                                 (swap! session update :handler-by-client-method-id dissoc client-method-id)
                                 (if (contains? message :error)
                                   (reject (:error message))
                                   (resolve (:result message))))]
          (swap! session update :handler-by-client-method-id assoc client-method-id response-handler)
          (send-message (-> message
                            (assoc :jsonrpc "2.0"
                                   :id client-method-id))))))))

(defn ping-handler [context]
  {})

(defn set-logging-level-handler [{:keys [session message]}]
  (let [client-logging-level (-> message :params :level)]
    (swap! session assoc :client-logging-level client-logging-level))
  {})

(defn completion-complete-handler [{:keys [session message] :as context}]
  (let [{:keys [ref argument]} (:params message)]
    (case (:type ref)
      "ref/prompt" (if-some [complete-fn (-> @session :prompt-by-name (get (:name ref)) :complete-fn)]
                     (complete-fn context (:name argument) (:value argument))
                     (json-rpc/method-not-found-response (:id message)))
      "ref/resource" (if-some [complete-fn (:resource-uri-complete-fn @session)]
                       (complete-fn context (:uri ref) (:name argument) (:value argument))
                       (json-rpc/method-not-found-response (:id message))))))

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
      (json-rpc/method-not-found-response (:id message)))))

(defn resource-list-handler [{:keys [session]}]
  {:resources (-> @session :resource-by-uri vals
                  (->> (mapv (fn [resource]
                               (select-keys resource [:uri :name :description :mimeType])))))
   #_#_
   :nextCursor "next-page-cursor"})

(defn resource-templates-list-handler [{:keys [session]}]
  {:resourceTemplates (-> @session (:resource-templates []))})

(defn resource-read-handler [{:keys [session message]}]
  (let [{:keys [uri]} (:params message)]
    (if-some [resource (-> @session :resource-by-uri (get uri))]
      {:contents [(select-keys resource [:uri :description :mimeType :text :blob])]} ; either text or blob
      (json-rpc/resource-not-found (:id message) uri))))

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
    (try
      (if-some [tool-fn (-> @session :tool-by-name (get name) :tool-fn)]
        (tool-fn context arguments)
        (json-rpc/invalid-tool-name (:id message) name))
      (catch Exception e
        {:content [{:type "text" :text (.getMessage e)}]
         :isError true}))))

(defn cancelled-notification-handler [{:keys [session message]}]
  (when-some [is-cancelled-atom (-> @session :is-cancelled-by-message-id (get (-> message :params :requestId)))]
    (reset! is-cancelled-atom true)))

(defn roots-changed-notification-handler [context]
  (let [{:keys [session]} context]
    ;; Let's ask the client what the roots are.
    (-> (call-remote-method context {:method "roots/list"})
        (p/then (fn [result]
                  ;; Replace the old roots by the new ones
                  (swap! session assoc :client-root-by-uri
                         (mc/index-by :uri (:roots result)))

                  (when-some [on-client-roots-updated (:on-client-roots-updated @session)]
                    (on-client-roots-updated context)))))))

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
  (swap! session assoc :initialized true)

  ;; Let's get the roots from the client
  (when (contains? (:client-capabilities @session) :roots)
    (roots-changed-notification-handler context)))
