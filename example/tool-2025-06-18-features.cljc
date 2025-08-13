(ns example.tool-2025-06-18
  "Example tool demonstrating 2025-06-18 features.
   
   This example shows:
   - Title field for better UI display
   - Structured output with outputSchema
   - Resource links in tool results
   - _meta field for metadata"
  (:require [mcp-toolkit.server :as server]
            [mcp-toolkit.impl.meta-support :as meta]
            [promesa.core :as p]))

;; Example 1: Simple tool with title field
(def simple-calculator
  {:name "simple_calc"
   :title "Simple Calculator" ;; NEW: Human-readable display name
   :description "Performs basic arithmetic operations"
   :inputSchema {:type "object"
                 :properties {:operation {:type "string"
                                          :enum ["add" "subtract" "multiply" "divide"]}
                              :a {:type "number"}
                              :b {:type "number"}}
                 :required [:operation :a :b]}
   :tool-fn (fn [context arguments]
              (let [{:keys [operation a b]} arguments
                    result (case operation
                             "add" (+ a b)
                             "subtract" (- a b)
                             "multiply" (* a b)
                             "divide" (/ a b))]
                ;; Simple string return still works (backward compatible)
                (str "Result: " result)))})

;; Example 2: Tool with structured output and outputSchema
(def advanced-calculator
  {:name "advanced_calc"
   :title "Advanced Calculator" ;; Display name
   :description "Calculator with structured output"
   :inputSchema {:type "object"
                 :properties {:operation {:type "string"
                                          :enum ["add" "subtract" "multiply" "divide"]}
                              :a {:type "number"}
                              :b {:type "number"}}
                 :required [:operation :a :b]}
   ;; NEW: Define the structure of the output
   :outputSchema {:type "object"
                  :properties {:result {:type "number"
                                        :description "The calculated result"}
                               :formula {:type "string"
                                         :description "The formula used"}
                               :precision {:type "integer"
                                           :description "Decimal precision"}}
                  :required [:result :formula]}
   :tool-fn (fn [context arguments]
              (let [{:keys [operation a b]} arguments
                    op-symbol (case operation
                                "add" "+"
                                "subtract" "-"
                                "multiply" "*"
                                "divide" "/")
                    result (case operation
                             "add" (+ a b)
                             "subtract" (- a b)
                             "multiply" (* a b)
                             "divide" (/ a b))
                    formula (str a " " op-symbol " " b " = " result)]
                ;; Return structured response
                {:content [{:type "text"
                            :text formula}]
                 :isError false}))})

;; Example 3: Tool that returns resources
(def file-analyzer
  {:name "analyze_files"
   :title "File Analyzer"
   :description "Analyzes project files and returns links to important resources"
   :inputSchema {:type "object"
                 :properties {:directory {:type "string"
                                          :description "Directory to analyze"}}
                 :required [:directory]}
   :tool-fn (fn [context arguments]
              (p/let [dir (:directory arguments)
                      ;; Simulate finding important files
                      important-files ["README.md" "deps.edn" "src/core.clj"]]
                ;; Return content with resource links
                {:content [{:type "text"
                            :text (str "Found " (count important-files)
                                       " important files in " dir)}]
                 ;; NEW: Include resource links
                 :resources (mapv (fn [file]
                                    {:uri (str "file://" dir "/" file)
                                     :name file
                                     :title (str "Project file: " file) ;; Resources can have titles too
                                     :mimeType (cond
                                                 (clojure.string/ends-with? file ".md") "text/markdown"
                                                 (clojure.string/ends-with? file ".edn") "application/edn"
                                                 (clojure.string/ends-with? file ".clj") "text/x-clojure"
                                                 :else "text/plain")})
                                  important-files)}))})

;; Example 4: Tool with metadata
(def data-processor
  {:name "process_data"
   :title "Data Processor with Metrics"
   :description "Processes data and includes performance metadata"
   :inputSchema {:type "object"
                 :properties {:data {:type "array"
                                     :items {:type "number"}}
                              :operation {:type "string"
                                          :enum ["sum" "average" "min" "max"]}}
                 :required [:data :operation]}
   :outputSchema {:type "object"
                  :properties {:result {:type "number"}
                               :count {:type "integer"}}}
   :tool-fn (fn [context arguments]
              (let [start-time (System/currentTimeMillis)
                    {:keys [data operation]} arguments
                    result (case operation
                             "sum" (reduce + 0 data)
                             "average" (/ (reduce + 0 data) (count data))
                             "min" (apply min data)
                             "max" (apply max data))
                    processing-time (- (System/currentTimeMillis) start-time)
                    response {:content [{:type "text"
                                         :text (str (clojure.string/capitalize operation)
                                                    " of " (count data) " numbers: " result)}]}]
                ;; Add metadata for tracking/debugging
                (meta/with-meta-field response
                  {:timestamp start-time
                   :processing-time-ms processing-time
                   :data-count (count data)
                   :operation operation})))})

;; Example 5: Prompt with completion that uses context
(def sql-query-prompt
  {:name "sql_query"
   :title "SQL Query Builder" ;; Display name for prompt
   :description "Helps build SQL queries with context-aware completions"
   :arguments [{:name "table"
                :description "Database table name"
                :required true}
               {:name "columns"
                :description "Columns to select"
                :required false}
               {:name "conditions"
                :description "WHERE conditions"
                :required false}]
   :prompt-fn (fn [context arguments]
                {:messages [{:role "user"
                             :content {:type "text"
                                       :text (str "Generate SQL query for table: " (:table arguments)
                                                  "\nColumns: " (:columns arguments)
                                                  "\nConditions: " (:conditions arguments))}}]})
   ;; Completion function that uses context
   :complete-fn (fn [context arg-name arg-value]
                  (let [;; NEW: Access context from previous completions
                        prev-context (:completion-context context)
                        prev-table (:table prev-context)]
                    (case arg-name
                      "table"
                      {:completion {:values ["users" "orders" "products" "customers"]
                                    :total 4
                                    :hasMore false}}

                      "columns"
                      ;; Use previous context to provide relevant columns
                      (if prev-table
                        {:completion {:values (case prev-table
                                                "users" ["id" "name" "email" "created_at"]
                                                "orders" ["id" "user_id" "total" "status"]
                                                "products" ["id" "name" "price" "category"]
                                                "customers" ["id" "company" "contact" "phone"]
                                                [])
                                      :hasMore false}}
                        {:completion {:values [] :hasMore false}})

                      "conditions"
                      ;; Context-aware condition suggestions
                      (if prev-table
                        {:completion {:values (case prev-table
                                                "users" ["created_at > '2024-01-01'" "email LIKE '%@example.com'"]
                                                "orders" ["status = 'pending'" "total > 100"]
                                                "products" ["price < 50" "category = 'electronics'"]
                                                [])
                                      :hasMore false}}
                        {:completion {:values [] :hasMore false}})

                      ;; Default
                      {:completion {:values [] :hasMore false}})))})

;; Example 6: Resource with title field
(def config-resource
  {:uri "config://app/settings"
   :name "app_settings"
   :title "Application Settings" ;; NEW: Display name for resource
   :description "Current application configuration"
   :mimeType "application/json"
   :read-fn (fn [context uri]
              {:contents [{:uri uri
                           :mimeType "application/json"
                           :text (str {:version "2025-06-18"
                                       :features {:titles true
                                                  :structured-output true
                                                  :resource-links true
                                                  :context true
                                                  :meta-fields true}})}]})})

;; Create a test session with all examples
(defn create-example-session []
  (server/create-session
   {:tools [simple-calculator
            advanced-calculator
            file-analyzer
            data-processor]
    :prompts [sql-query-prompt]
    :resources [config-resource]}))

;; Helper function to test tools directly
(defn test-tool [tool-name arguments]
  (let [session (atom (create-example-session))
        context {:session session}
        tool (-> @session :tool-by-name (get tool-name))]
    (if tool
      @((:tool-fn tool) context arguments)
      (str "Tool not found: " tool-name))))

;; Example usage:
(comment
  ;; Test simple calculator
  (test-tool "simple_calc" {:operation "add" :a 10 :b 20})
  ;; => "Result: 30"

  ;; Test advanced calculator with structured output
  (test-tool "advanced_calc" {:operation "multiply" :a 7 :b 8})
  ;; => {:content [{:type "text", :text "7 * 8 = 56"}], :isError false}

  ;; Test file analyzer with resource links
  (test-tool "analyze_files" {:directory "/my/project"})
  ;; => {:content [...], :resources [{:uri "file:///my/project/README.md", ...}]}

  ;; Test data processor with metadata
  (test-tool "process_data" {:data [1 2 3 4 5] :operation "average"})
  ;; => {:content [...], :_meta {:timestamp ..., :processing-time-ms ...}}
  )
