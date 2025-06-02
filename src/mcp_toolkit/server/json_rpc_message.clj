(ns mcp-toolkit.server.json-rpc-message)

;; https://www.jsonrpc.org/specification

;; rpc call with invalid JSON:
(def parse-error-response
  {:jsonrpc "2.0"
   :error {:code -32700
           :message "Parse error"}
   :id nil})

;; rpc call of non-existent method:
(defn method-not-found-response [id]
  {:jsonrpc "2.0"
   :error {:code -32601
           :message "Method not found"}
   :id id})

;; rpc call with invalid Request object:
(def invalid-request-response
  {:jsonrpc "2.0"
   :error {:code -32600
           :message "Invalid Request"}
   :id nil})

(defn resource-not-found [id uri]
  {:jsonrpc "2.0"
   :error {:code -32002
           :message "Resource not found"
           :data {:uri uri}}
   :id id})

(defn invalid-tool-name [id tool-name]
  {:jsonrpc "2.0"
   :error {:code -32602
           :message (str "Unknown tool: " tool-name)}
   :id id})

(defn notification
  ([topic]
   {:jsonrpc "2.0"
    :method (str "notifications/" topic)})
  ([topic params]
   (-> (notification topic)
       (assoc :params params))))
