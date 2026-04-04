(ns mcp-toolkit.streamable-http-test
  "HTTP integration tests for streamable_http transport.
   Tests POST/GET/DELETE cycles, session management, protocol versioning,
   origin validation, and session lifecycle per MCP 2025-11-25 spec."
  (:require [clojure.test :refer [deftest is testing]]
            [mcp-toolkit.test.fixtures :as fix]
            [mcp-toolkit.transport.streamable-http :as transport]))

;; Helper: initialize handler returns proper JSON-RPC response
(defn- init-dispatcher
  []
  (fn [msg _sid]
    {:jsonrpc "2.0"
     :id (:id msg)
     :result {:protocolVersion "2025-11-25"
              :capabilities {:tools {:listChanged true}}
              :serverInfo {:name "test-server" :version "0.1.0"}}}))

;; ─── POST /mcp Tests ──────────────────────────────────────────────────────

(deftest initialize-creates-session
  (testing "POST with initialize returns 200 + MCP-Session-Id"
    (let [store (transport/create-session-store)
          handler (transport/create-handler (init-dispatcher) {:session-store store})
          resp (fix/post-json handler "/mcp"
                              {:jsonrpc "2.0"
                               :method "initialize"
                               :id 1
                               :params {:protocolVersion "2025-11-25"}})
          session-id (get-in resp [:headers "MCP-Session-Id"])]
      (is (= 200 (:status resp)))
      (is (string? session-id))
      (is (> (count session-id) 10)))))

(deftest post-with-invalid-origin
  (testing "POST with bad Origin returns 403"
    (let [handler (transport/create-handler (init-dispatcher)
                                            {:allowed-origins ["https://example.com"]})
          resp (fix/handler-response handler :post "/mcp"
                                     {:headers {"Content-Type" "application/json"
                                                "Origin" "https://evil.com"}
                                      :body {:jsonrpc "2.0"
                                             :method "initialize"
                                             :id 1
                                             :params {}}})]
      (is (= 403 (:status resp))))))

(deftest post-with-missing-body
  (testing "POST with no body returns 400"
    (let [handler (transport/create-handler (fn [_ _] nil))
          resp (fix/handler-response handler :post "/mcp"
                                     {:headers {"Content-Type" "application/json"}}
                                     nil)]
      (is (= 400 (:status resp))))))

(deftest post-with-invalid-json
  (testing "POST with invalid JSON returns 400"
    (let [handler (transport/create-handler (fn [_ _] nil))
          resp (fix/handler-response handler :post "/mcp"
                                     {:headers {"Content-Type" "application/json"}}
                                     "NOT VALID")]
      (is (= 400 (:status resp))))))

(deftest post-with-unknown-session-id
  (testing "POST with unknown session-id returns 400"
    (let [handler (transport/create-handler (fn [_ _] nil))
          resp (fix/handler-response handler :post "/mcp"
                                     {:headers {"Content-Type" "application/json"
                                                "MCP-Session-Id" "nope"}
                                      :body {:jsonrpc "2.0"
                                             :method "tools/list"
                                             :id 2}})]
      (is (= 400 (:status resp))))))

(deftest post-notification-returns-202
  (testing "POST with notification (no :id) returns 202"
    (let [store (transport/create-session-store)
          handler (transport/create-handler (fn [_ _] nil) {:session-store store})
          resp1 (fix/post-json handler "/mcp"
                               {:jsonrpc "2.0"
                                :method "initialize"
                                :id 1
                                :params {:protocolVersion "2025-11-25"}})
          sid (get-in resp1 [:headers "MCP-Session-Id"])
          resp2 (fix/handler-response handler :post "/mcp"
                                      {:headers {"Content-Type" "application/json"
                                                 "MCP-Session-Id" sid}
                                       :body {:jsonrpc "2.0"
                                              :method "notifications/initialized"}})]
      (is sid)
      (is (= 202 (:status resp2))))))

;; ─── DELETE /mcp Tests ──────────────────────────────────────────────────

(deftest delete-session-success
  (testing "DELETE valid session returns 200"
    (let [store (transport/create-session-store)
          handler (transport/create-handler (init-dispatcher) {:session-store store})
          resp1 (fix/post-json handler "/mcp"
                               {:jsonrpc "2.0" :method "initialize" :id 1
                                :params {:protocolVersion "2025-11-25"}})
          sid (get-in resp1 [:headers "MCP-Session-Id"])
          resp2 (fix/delete-request handler "/mcp" {"MCP-Session-Id" sid})]
      (is (= 200 (:status resp1)))
      (is (= 200 (:status resp2))))))

(deftest delete-already-terminated-session
  (testing "DELETE already-terminated returns 200"
    (let [store (transport/create-session-store)
          handler (transport/create-handler (init-dispatcher) {:session-store store})
          resp1 (fix/post-json handler "/mcp"
                               {:jsonrpc "2.0" :method "initialize" :id 1
                                :params {:protocolVersion "2025-11-25"}})
          sid (get-in resp1 [:headers "MCP-Session-Id"])
          _ (fix/delete-request handler "/mcp" {"MCP-Session-Id" sid})
          resp2 (fix/delete-request handler "/mcp" {"MCP-Session-Id" sid})]
      (is (= 200 (:status resp2))))))

(deftest delete-nonexistent-session
  (testing "DELETE nonexistent session returns 404"
    (let [handler (transport/create-handler (fn [_ _] nil))
          resp (fix/delete-request handler "/mcp" {"MCP-Session-Id" "nope"})]
      (is (= 404 (:status resp))))))

(deftest delete-without-session-id
  (testing "DELETE without session-id returns 400"
    (let [handler (transport/create-handler (fn [_ _] nil))
          resp (fix/delete-request handler "/mcp")]
      (is (= 400 (:status resp))))))

;; ─── POST After DELETE Tests ────────────────────────────────────────────

(deftest post-after-delete-returns-404
  (testing "POST with terminated session returns 404"
    (let [store (transport/create-session-store)
          handler (transport/create-handler (init-dispatcher) {:session-store store})
          resp1 (fix/post-json handler "/mcp"
                               {:jsonrpc "2.0" :method "initialize" :id 1
                                :params {:protocolVersion "2025-11-25"}})
          sid (get-in resp1 [:headers "MCP-Session-Id"])
          _ (fix/delete-request handler "/mcp" {"MCP-Session-Id" sid})
          resp2 (fix/handler-response handler :post "/mcp"
                                      {:headers {"Content-Type" "application/json"
                                                 "MCP-Session-Id" sid}
                                       :body {:jsonrpc "2.0" :method "tools/list" :id 2}})]
      (is (= 404 (:status resp2))))))

;; ─── GET /mcp Tests ─────────────────────────────────────────────────────

(deftest get-without-session-id
  (testing "GET without session-id returns 400"
    (let [handler (transport/create-handler (fn [_ _] nil))
          resp (fix/get-request handler "/mcp")]
      (is (= 400 (:status resp))))))

(deftest get-with-invalid-session
  (testing "GET with invalid session returns 404"
    (let [handler (transport/create-handler (fn [_ _] nil))
          resp (fix/get-request handler "/mcp" {"MCP-Session-Id" "nope"})]
      (is (= 404 (:status resp))))))

(deftest get-with-terminated-session
  (testing "GET with terminated session returns 404"
    (let [store (transport/create-session-store)
          sid (transport/create-session! store)
          _ (transport/delete-session! store sid)
          _ (transport/create-session! store)
          handler (transport/create-handler (fn [_ _] nil) {:session-store store})
          resp (fix/handler-response handler :get "/mcp"
                                     {:headers {"MCP-Session-Id" sid}})]
      (is (= 404 (:status resp))))))

;; ─── Health / 405 / 404 ─────────────────────────────────────────────────

(deftest health-endpoint
  (testing "GET /health returns 200 ok"
    (let [resp (fix/handler-response
                (transport/create-handler (fn [_ _] nil))
                :get "/health")]
      (is (= 200 (:status resp)))
      (is (re-find #"ok" (:body resp))))))

(deftest unsupported-method-on-mcp
  (testing "PUT /mcp returns 405"
    (let [resp (fix/handler-response
                (transport/create-handler (fn [_ _] nil))
                :put "/mcp")]
      (is (= 405 (:status resp))))))

(deftest unknown-uri
  (testing "Unknown URI returns 404"
    (let [resp (fix/handler-response
                (transport/create-handler (fn [_ _] nil))
                :get "/unknown")]
      (is (= 404 (:status resp))))))

;; ─── Session Store Lifecycle Tests ──────────────────────────────────────

(deftest create-session-store-structure
  (testing "create-session-store returns {:sessions {} :terminated {} :pending-sse-messages {}}"
    (let [store (transport/create-session-store)]
      (is (= {:sessions {} :terminated {} :pending-sse-messages {}} @store)))))

(deftest create-session!-adds-to-sessions
  (testing "create-session! adds to :sessions"
    (let [store (transport/create-session-store)
          sid (transport/create-session! store)]
      (is (contains? (:sessions @store) sid)))))

(deftest valid-session?-checks-sessions
  (testing "valid-session? checks :sessions"
    (let [store (transport/create-session-store)
          sid (transport/create-session! store)]
      (is (transport/valid-session? store sid))
      (is (not (transport/valid-session? store "nope"))))))

(deftest terminated-session?-checks-terminated
  (testing "terminated-session? detects tombstoned sessions"
    (let [store (transport/create-session-store)
          sid (transport/create-session! store)]
      (is (not (transport/terminated-session? store sid)))
      (transport/delete-session! store sid)
      (is (transport/terminated-session? store sid)))))

(deftest delete-session!-returns-keywords
  (testing "delete-session! returns proper keywords"
    (let [store (transport/create-session-store)
          sid (transport/create-session! store)]
      (is (= :existed (transport/delete-session! store sid)))
      (is (= :already-terminated (transport/delete-session! store sid)))
      (is (= :not-found (transport/delete-session! store "nope")))
      (is (= :not-found (transport/delete-session! store nil))))))

(deftest delete-session-atomic-tombstone
  (testing "delete moves session to terminated"
    (let [store (transport/create-session-store)
          sid (transport/create-session! store)]
      (is (contains? (:sessions @store) sid))
      (transport/delete-session! store sid)
      (is (not (contains? (:sessions @store) sid)))
      (is (contains? (:terminated @store) sid)))))

(deftest prune-expired-sessions-works
  (testing "prune-expired-sessions! removes terminated under TTL"
    (let [store (transport/create-session-store)
          sid (transport/create-session! store)]
      (transport/delete-session! store sid)
      ;; Force terminated-at to be old
      (swap! store assoc-in [:terminated sid :terminated-at] 0)
      (transport/prune-expired-sessions! store 1000)
      (is (empty? (:terminated @store))))))
