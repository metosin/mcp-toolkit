(ns mcp-toolkit.json-rpc
  (:require [promesa.core :as p]))

;;
;; https://www.jsonrpc.org/specification
;;

;; RPC call with invalid JSON:
(def parse-error-response
  {:jsonrpc "2.0"
   :error {:code -32700
           :message "Parse error"}
   :id nil})

;; RPC call of non-existent method:
(defn method-not-found-response
  "Creates a JSON-RPC error response for when a requested method is not found.

   Args:
     id - The request ID from the original method call

   Returns:
     A JSON-RPC error response map with method not found error (-32601)."
  [id]
  {:jsonrpc "2.0"
   :error {:code -32601
           :message "Method not found"}
   :id id})

;; RPC call with invalid Request object:
(def invalid-request-response
  {:jsonrpc "2.0"
   :error {:code -32600
           :message "Invalid Request"}
   :id nil})

(defn resource-not-found
  "Creates a JSON-RPC error response for when a requested resource is not found.

   Args:
     id  - The request ID from the original method call
     uri - The URI of the resource that was not found

   Returns:
     A JSON-RPC error response map with resource not found error (-32002)."
  [id uri]
  {:jsonrpc "2.0"
   :error {:code -32002
           :message "Resource not found"
           :data {:uri uri}}
   :id id})

(defn invalid-tool-name
  "Creates a JSON-RPC error response for when a tool name is invalid or unknown.

   Args:
     id        - The request ID from the original method call
     tool-name - The name of the tool that was invalid/unknown

   Returns:
     A JSON-RPC error response map with invalid params error (-32602)."
  [id tool-name]
  {:jsonrpc "2.0"
   :error {:code -32602
           :message (str "Unknown tool: " tool-name)}
   :id id})

(defn notification
  "Creates a JSON-RPC notification message.

   Args:
     topic  - The notification topic (string)
     params - (Optional) Parameters map to include with the notification

   Returns:
     A JSON-RPC notification map with method set to 'notifications/<topic>'."
  ([topic]
   {:jsonrpc "2.0"
    :method (str "notifications/" topic)})
  ([topic params]
   (-> (notification topic)
       (assoc :params params))))


;;
;;
;;

(defn call-remote-method
  "Calls a remote method via JSON-RPC.
   Returns a promise which either resolves with the message's result or
   rejects with the message's error.

   Args:
     context - The session context containing session state and send-message function
     message - Map with :method and optional :params keys

   Returns:
     A promise that resolves to the method result or rejects with the error."
  [context {:keys [method params] :as message}]
  (let [{:keys [session send-message]} context
        ;; Picks a unique method id for a remote call. Robust to concurrent calls.
        ;; TODO: ensure it loops when reaching the maximum integer value.
        called-method-id (-> (swap! session update :last-called-method-id inc)
                             :last-called-method-id)]
    (p/create
      (fn [resolve reject]
        (let [response-handler (fn [{:keys [session message]}]
                                 (swap! session update :handler-by-called-method-id dissoc called-method-id)
                                 (if (contains? message :error)
                                   (reject (:error message))
                                   (resolve (:result message))))]
          (swap! session update :handler-by-called-method-id assoc called-method-id response-handler)
          (send-message (-> message
                            (assoc :jsonrpc "2.0"
                                   :id called-method-id))))))))

(defn send-message
  "Sends a message using the context's send-message function.

   Args:
     context - The session context containing the send-message function
     message - The message to send

   Returns:
     The result of calling the send-message function."
  [context message]
  (let [{:keys [send-message]} context]
    (send-message message)))

(defn close-connection
  "Closes the connection if a close-connection function is available in the context.

   Args:
     context - The session context that may contain a close-connection function

   Returns:
     The result of calling close-connection, or nil if not available."
  [context]
  (when-some [close-connection (:close-connection context)]
    (close-connection)))

(defn- route-message
  "Returns a Promesa promise which handles a given json-rpc-message."
  [{:keys [session message] :as context}]
  (if (contains? message :method)
    (let [{:keys [id method]} message
          handler (-> @session :handler-by-method (get method))]
      (if (nil? handler)
        (method-not-found-response id)
        (if (nil? id)
          ;; Notification, shall not return a result
          (do
            (handler context)
            nil)
          ;; Method call, cancellable, with result value when not cancelled
          (let [is-cancelled (atom false)
                context (assoc context :is-cancelled is-cancelled)]
            (swap! session update :is-cancelled-by-request-id assoc id is-cancelled)
            (-> (handler context)
                (p/then (fn [result]
                          (when-not @is-cancelled
                            {:jsonrpc "2.0"
                             :result result
                             :id id})))
                (p/handle (fn [result error]
                            ;; Clean up, side effect
                            (swap! session update :is-cancelled-by-request-id dissoc id)

                            ;; Pass through as if this p/handle was not there.
                            ;; We avoided using p/finally because it does not allow chaining further promises.
                            (or error result))))))))
    ;; Method call response
    (if (and (contains? message :id)
             (or (contains? message :result)
                 (contains? message :error)))
      (if-some [handler (-> @session :handler-by-called-method-id (get (:id message)))]
        (do
          (handler context)
          nil)
        ;; TODO: handle the case where the id is unknown to us.
        ,)
      ;; TODO: handle the message's structural problem.
      ,)))

(defn handle-message
  "Handles incoming JSON-RPC messages, supporting both single messages and batch requests.
   Routes messages to appropriate handlers and manages responses.

   Args:
     context - The session context containing message, session, and send-message

   Returns:
     A promise that resolves when message handling is complete."
  [context]
  (let [{:keys [message send-message]} context]
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
                      (send-message response))))))))
