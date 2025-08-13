#!/usr/bin/env bb
;; Quick test script for validating 2025-06-18 compatibility
;; Run with: bb test-migration.clj

(require '[clojure.edn :as edn]
         '[clojure.pprint :as pp])

(defn check-tool-compatibility
  "Check if a tool definition is compatible with 2025-06-18"
  [tool]
  (let [issues (atom [])
        warnings (atom [])]

    ;; Check for required fields
    (when-not (:name tool)
      (swap! issues conj "Missing required field: :name"))

    ;; Check for recommended fields
    (when-not (:title tool)
      (swap! warnings conj "Missing recommended field: :title (human-readable display name)"))

    ;; Check tool function
    (when (:tool-fn tool)
      (when-not (fn? (:tool-fn tool))
        (swap! issues conj ":tool-fn must be a function")))

    ;; Check for outputSchema (optional but recommended)
    (when-not (:outputSchema tool)
      (swap! warnings conj "Consider adding :outputSchema for type-safe responses"))

    {:tool-name (:name tool)
     :title (:title tool)
     :compatible? (empty? @issues)
     :issues @issues
     :warnings @warnings}))

(defn check-prompt-compatibility
  "Check if a prompt definition is compatible with 2025-06-18"
  [prompt]
  (let [issues (atom [])
        warnings (atom [])]

    ;; Check for required fields
    (when-not (:name prompt)
      (swap! issues conj "Missing required field: :name"))

    ;; Check for recommended fields
    (when-not (:title prompt)
      (swap! warnings conj "Missing recommended field: :title (human-readable display name)"))

    {:prompt-name (:name prompt)
     :title (:title prompt)
     :compatible? (empty? @issues)
     :issues @issues
     :warnings @warnings}))

(defn check-resource-compatibility
  "Check if a resource definition is compatible with 2025-06-18"
  [resource]
  (let [issues (atom [])
        warnings (atom [])]

    ;; Check for required fields
    (when-not (:uri resource)
      (swap! issues conj "Missing required field: :uri"))

    ;; Check for recommended fields
    (when-not (:title resource)
      (swap! warnings conj "Missing recommended field: :title (human-readable display name)"))

    {:resource-uri (:uri resource)
     :title (:title resource)
     :compatible? (empty? @issues)
     :issues @issues
     :warnings @warnings}))

(defn analyze-tool-response
  "Check if a tool response follows 2025-06-18 structure"
  [response]
  (cond
    ;; String response (backward compatible)
    (string? response)
    {:type :simple-string
     :compatible? true
     :note "Simple string return - will be auto-wrapped in content"}

    ;; Structured response
    (and (map? response)
         (or (contains? response :content)
             (contains? response :resources)))
    {:type :structured
     :compatible? true
     :has-content? (contains? response :content)
     :has-resources? (contains? response :resources)
     :has-meta? (contains? response :_meta)}

    ;; Other map response (might need updating)
    (map? response)
    {:type :unknown-map
     :compatible? false
     :note "Map response should have :content or :resources field"
     :suggestion "Wrap in {:content [{:type \"text\" :text (pr-str response)}]}"}

    ;; Other types
    :else
    {:type :other
     :compatible? false
     :note "Response should be string or structured map"}))

(defn test-session-compatibility
  "Test a complete session configuration for 2025-06-18 compatibility"
  [session-config]
  (println "\n=== MCP Toolkit 2025-06-18 Compatibility Check ===\n")

  ;; Check tools
  (when-let [tools (:tools session-config)]
    (println "## Tools")
    (doseq [tool tools]
      (let [result (check-tool-compatibility tool)]
        (println (str "  " (:tool-name result)
                      (when (:title result) (str " (\"" (:title result) "\")"))
                      ": " (if (:compatible? result) "✅ Compatible" "❌ Issues found")))
        (doseq [issue (:issues result)]
          (println (str "    ERROR: " issue)))
        (doseq [warning (:warnings result)]
          (println (str "    WARN: " warning)))))
    (println))

  ;; Check prompts
  (when-let [prompts (:prompts session-config)]
    (println "## Prompts")
    (doseq [prompt prompts]
      (let [result (check-prompt-compatibility prompt)]
        (println (str "  " (:prompt-name result)
                      (when (:title result) (str " (\"" (:title result) "\")"))
                      ": " (if (:compatible? result) "✅ Compatible" "❌ Issues found")))
        (doseq [issue (:issues result)]
          (println (str "    ERROR: " issue)))
        (doseq [warning (:warnings result)]
          (println (str "    WARN: " warning)))))
    (println))

  ;; Check resources
  (when-let [resources (:resources session-config)]
    (println "## Resources")
    (doseq [resource resources]
      (let [result (check-resource-compatibility resource)]
        (println (str "  " (:resource-uri result)
                      (when (:title result) (str " (\"" (:title result) "\")"))
                      ": " (if (:compatible? result) "✅ Compatible" "❌ Issues found")))
        (doseq [issue (:issues result)]
          (println (str "    ERROR: " issue)))
        (doseq [warning (:warnings result)]
          (println (str "    WARN: " warning)))))
    (println))

  (println "## Summary")
  (println "  - Add :title fields for better UI display")
  (println "  - Consider adding :outputSchema to tools")
  (println "  - Tool responses can now include :resources")
  (println "  - Completion handlers can access :completion-context")
  (println "  - Use mcp-toolkit.impl.meta-support for metadata")
  (println "\nSee MIGRATION-2025-06-18.md for detailed upgrade instructions"))

;; Example usage
(def example-old-tool
  {:name "old_tool"
   :description "An old tool without title"
   :inputSchema {:type "object"}
   :tool-fn (fn [ctx args] "result")})

(def example-new-tool
  {:name "new_tool"
   :title "New Tool"
   :description "A new tool with title"
   :inputSchema {:type "object"}
   :outputSchema {:type "object"
                  :properties {:result {:type "string"}}}
   :tool-fn (fn [ctx args]
              {:content [{:type "text" :text "result"}]})})

;; Run the test
(println "Testing example tools...")
(test-session-compatibility {:tools [example-old-tool example-new-tool]})

(println "\n=== Tool Response Analysis ===\n")
(println "Old style response: " (analyze-tool-response "simple string"))
(println "New style response: " (analyze-tool-response {:content [{:type "text" :text "hi"}]}))
(println "Invalid response: " (analyze-tool-response {:data "value"}))

(println "\n✅ Test complete. Check the output above for compatibility issues.")
