(ns mcp-toolkit.core-test
  (:require [clojure.test :refer [deftest testing is are #?(:cljs async)]]
            [mcp-toolkit.json-rpc :as json-rpc]
            [mcp-toolkit.client :as client]
            [mcp-toolkit.server :as server]
            [mcp-toolkit.test.util :as util]
            [promesa.core :as p]
            [promesa.exec.csp :as sp]))

(def test-prompt
  {:name "test_prompt"
   :description "Test prompt"
   :arguments [{:description "A test argument"
                :name "x"
                :required false}]})

(def test-resource
  {:uri "ipfs:///world/hello.md"
   :name "hello.md"
   :description "Hello world"
   :mimeType "text/markdown"})

(def test-tool
  {:name "parentify"
   :description "Parentify a text: wraps a text within parenthesis."
   :inputSchema {:properties {:text {:description "the text to be parentified"
                                     :type "string"}}
                 :type "object"
                 :required ["text"]}})

(defn setup-and-connect-client-server
  "Returns a "
  [client-session server-session message-logs]
  (let [;; Message transport
        client-output (sp/chan)
        server-output (sp/chan)

        ;; Client
        client-context {:session (atom client-session)
                        :send-message (fn [message]
                                        (swap! message-logs conj [:-> message]) ; Used for tests
                                        (sp/put client-output message))         ; Transport client -> server
                        :close-output (fn []
                                        (sp/close! client-output))}

        ;; Server
        server-context {:session (atom server-session)
                        :send-message (fn [message]
                                        (swap! message-logs conj [:<- message]) ; Used for tests
                                        (sp/put server-output message))         ; Transport server -> client
                        :close-output (fn []
                                        (sp/close! server-output))}

        message-processing-loop (fn [context input-channel]
                                  (p/loop []
                                    (p/let [message (sp/take input-channel)]
                                      (when (some? message) ; A nil message means that the channel was closed.
                                        (p/do
                                          (json-rpc/handle-message (-> context
                                                                       (assoc :message message)))
                                          (p/recur))))))]
    ;; A promise for each site, to run a message processing loop.
    (message-processing-loop client-context server-output)
    (message-processing-loop server-context client-output)

    {:client-context client-context
     :server-context server-context}))

(defn promesa-async-test [timeout p]
  #?(:cljs (async done
             (-> (p/timeout p timeout ::timeout)
                 (p/handle (fn [x error]
                             (when (= x ::timeout)
                               (is nil (str "Time out error: the test did not finish after waiting " timeout "ms.")))
                             (or error x)))
                 (p/then (fn [_] (done)))))
     :clj (-> (p/timeout p timeout ::timeout)
              (p/handle (fn [x error]
                          (when (= x ::timeout)
                            (is nil (str "Time out error: the test did not finish after waiting " timeout "ms.")))
                          (or error x)))
              deref)))

(deftest handshake-test
  (is true "yes") ;; <-- this resolves a warning for a missing `(is ,,,)` in CLJ

  (promesa-async-test 3000
    (testing "the MCP handshake"
      (let [message-logs (atom [])
            client-session (client/create-session {;; do not automatically request the prompts, resources and tools
                                                   :on-initialized nil})
            server-session (server/create-session {;; do not automatically request the roots
                                                   :on-initialized nil})
            {:keys [client-context server-context]} (setup-and-connect-client-server client-session
                                                                                     server-session
                                                                                     message-logs)]
        (-> (p/do
              ;; Initiate the client-server communication
              (client/send-first-handshake-message client-context)

              ;; Wait until we have enough messages for the test.
              (util/assert-atom message-logs
                                (fn [logs] (= (count logs) 3))
                                3000
                                "MCP handshake")

              ;; Test if the messages are what we expect.
              (is (= [[:-> {:jsonrpc "2.0"
                            :method "initialize"
                            :params {:clientInfo {:name "mcp-toolkit"
                                                  :version "0.0.1"}
                                     :protocolVersion "2025-03-26"
                                     :capabilities {:roots {:listChanged true}}}
                            :id 0}]
                      [:<- {:jsonrpc "2.0"
                            :result {:serverInfo {:name "mcp-toolkit"
                                                  :version "0.0.1"}
                                     :protocolVersion "2025-03-26"
                                     :capabilities {:logging {}
                                                    :completions {}
                                                    :prompts {:listChanged true}
                                                    :resources {:listChanged true
                                                                :subscribe true}
                                                    :tools {:listChanged true}}}
                            :id 0}]
                      [:-> {:jsonrpc "2.0"
                            :method "notifications/initialized"}]]
                     @message-logs)))
            (p/handle (fn [x error]
                        ((:close-output client-context))
                        ((:close-output server-context))

                        ;; Pass through
                        (or error x))))))))
