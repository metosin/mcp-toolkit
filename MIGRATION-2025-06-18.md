# MCP Toolkit 2025-06-18 Migration Guide

This guide helps you upgrade your MCP servers and clients from protocol versions 2025-03-26 (or earlier) to 2025-06-18.

## Overview of Changes

The 2025-06-18 specification introduces several enhancements and one breaking change:

### Breaking Changes
- **JSON-RPC batching removed** - Array requests are no longer supported

### New Features
- **Title fields** - Human-readable display names for prompts, resources, and tools
- **Structured tool output** - Tools can define output schemas for type-safe responses
- **Resource links in tool results** - Tools can return resources alongside content
- **Context in completions** - Completion requests can include previously-resolved variables
- **_meta field support** - Optional metadata fields for various message types

## Migration Steps

### Step 1: Update Your Dependencies

Update your `deps.edn` to use the latest version of mcp-toolkit:

```clojure
{:deps {fi.metosin/mcp-toolkit {:mvn/version "0.2.0-alpha"}}} ; or latest version
```

### Step 2: Update Protocol Version (Optional)

The toolkit automatically negotiates the best protocol version between client and server. However, if you want to explicitly use 2025-06-18:

**For Servers:**
```clojure
;; The server automatically supports 2025-06-18 alongside older versions
;; No changes needed - backward compatibility is maintained
```

**For Clients:**
```clojure
(def session 
  (client/create-session 
    {:protocol-version "2025-06-18"  ;; Explicitly request 2025-06-18
     ;; ... other options
     }))
```

### Step 3: Add Title Fields (Recommended)

Add human-readable `title` fields to improve UI display in MCP clients like Claude Desktop:

#### Before (2025-03-26):
```clojure
(def my-tool
  {:name "calculate_sum"
   :description "Calculates the sum of two numbers"
   :inputSchema {:type "object"
                 :properties {:a {:type "number"}
                              :b {:type "number"}}}
   :tool-fn (fn [context arguments]
              (str (+ (:a arguments) (:b arguments))))})
```

#### After (2025-06-18):
```clojure
(def my-tool
  {:name "calculate_sum"
   :title "Calculator - Addition"  ;; NEW: Human-readable display name
   :description "Calculates the sum of two numbers"
   :inputSchema {:type "object"
                 :properties {:a {:type "number"}
                              :b {:type "number"}}}
   :tool-fn (fn [context arguments]
              (str (+ (:a arguments) (:b arguments))))})
```

The same applies to prompts and resources:

```clojure
(def my-prompt
  {:name "code_review_prompt"
   :title "Code Review Assistant"  ;; NEW: Display name
   :description "Reviews code for best practices"
   ;; ...
   })

(def my-resource
  {:uri "file:///docs/readme"
   :name "readme"
   :title "Project README"  ;; NEW: Display name
   :description "Main project documentation"
   :mimeType "text/markdown"
   ;; ...
   })
```

### Step 4: Update Tool Responses (Important)

Tools should now return structured responses with explicit `content` and optional `resources`:

#### Before (2025-03-26):
```clojure
(def simple-tool
  {:name "read_file"
   :tool-fn (fn [context arguments]
              ;; Simple string return
              "File contents here")})
```

#### After (2025-06-18) - Option 1: Keep Simple Return (Backward Compatible):
```clojure
(def simple-tool
  {:name "read_file"
   :title "File Reader"
   :tool-fn (fn [context arguments]
              ;; Still works - automatically wrapped in content
              "File contents here")})
```

#### After (2025-06-18) - Option 2: Structured Response (Recommended):
```clojure
(def structured-tool
  {:name "read_file"
   :title "File Reader"
   :tool-fn (fn [context arguments]
              ;; Return structured response
              {:content [{:type "text"
                         :text "File contents here"}]
               :isError false})})
```

#### After (2025-06-18) - Option 3: With Resource Links (New Feature):
```clojure
(def resource-aware-tool
  {:name "analyze_project"
   :title "Project Analyzer"
   :tool-fn (fn [context arguments]
              {:content [{:type "text"
                         :text "Found 3 configuration files"}]
               :resources [{:uri "file:///project/config.edn"
                           :name "config.edn"
                           :mimeType "application/edn"}
                          {:uri "file:///project/deps.edn"
                           :name "deps.edn"  
                           :mimeType "application/edn"}]})})
```

### Step 5: Add Output Schemas (Optional but Recommended)

Define the structure of your tool's output for better type safety:

```clojure
(def calculator-tool
  {:name "calculator"
   :title "Advanced Calculator"
   :description "Performs mathematical operations"
   :inputSchema {:type "object"
                 :properties {:operation {:type "string"
                                          :enum ["add" "subtract" "multiply" "divide"]}
                              :a {:type "number"}
                              :b {:type "number"}}
                 :required [:operation :a :b]}
   ;; NEW: Define output structure
   :outputSchema {:type "object"
                  :properties {:result {:type "number"}
                               :formula {:type "string"}
                               :unit {:type "string"}}
                  :required [:result :formula]}
   :tool-fn (fn [context arguments]
              (let [{:keys [operation a b]} arguments
                    result (case operation
                            "add" (+ a b)
                            "subtract" (- a b)
                            "multiply" (* a b)
                            "divide" (/ a b))]
                {:content [{:type "text"
                           :text (str "Result: " result)}]
                 ;; Structured data matching outputSchema
                 :data {:result result
                        :formula (str a " " operation " " b " = " result)
                        :unit "numeric"}})})
```

### Step 6: Handle Completion Context (Optional)

If you implement completion handlers, you can now access context from previous completions:

```clojure
(def my-prompt
  {:name "sql_query"
   :title "SQL Query Builder"
   :arguments [{:name "table" :required true}
               {:name "columns" :required false}]
   :complete-fn (fn [context arg-name arg-value]
                  ;; NEW: Check for completion context
                  (let [prev-context (:completion-context context)]
                    (cond
                      (= arg-name "table")
                      {:completion {:values ["users" "orders" "products"]
                                   :total 3
                                   :hasMore false}}
                      
                      (= arg-name "columns")
                      ;; Use context if available
                      (if-let [prev-table (:table prev-context)]
                        {:completion {:values (get {"users" ["id" "name" "email"]
                                                   "orders" ["id" "user_id" "total"]
                                                   "products" ["id" "name" "price"]}
                                                  prev-table [])
                                     :hasMore false}}
                        {:completion {:values [] :hasMore false}}))))})
```

### Step 7: Add Metadata Support (Optional)

Use the `_meta` field for passing metadata through the protocol:

```clojure
(require '[mcp-toolkit.impl.meta-support :as meta])

;; In your tool implementation
(def tracking-tool
  {:name "process_data"
   :title "Data Processor"
   :tool-fn (fn [context arguments]
              (let [result (process-data arguments)
                    response {:content [{:type "text"
                                        :text (:summary result)}]}]
                ;; Add metadata for tracking/debugging
                (meta/with-meta-field response
                                     {:timestamp (System/currentTimeMillis)
                                      :processing-time-ms (:duration result)
                                      :record-count (:count result)})))})
```

## Testing Your Migration

### 1. Run the Compatibility Checker

Use the provided test script to check your existing tools:

```bash
# Check your existing session configuration
bb test-migration.clj

# Or integrate into your code:
(load-file "test-migration.clj")
(test-session-compatibility your-session-config)
```

### 2. Run the Test Suite

After updating, ensure all tests pass:

```bash
clojure -M:test
```

### 2. Test Protocol Negotiation

Your server should work with both old and new clients:

```clojure
;; Test with MCP Inspector
npx @modelcontextprotocol/inspector clojure -X:mcp-server

;; The server should negotiate the appropriate protocol version
```

### 3. Verify Title Fields

Check that titles appear correctly in Claude Desktop or other MCP clients:
- Tools should show their title in the UI
- Resources should display with their title
- Prompts should use title for display

### 4. Test Structured Output

Verify that tools returning structured data work correctly:

```clojure
;; In REPL, test your tool directly
(def test-context {:session (atom {})})
(def result @((:tool-fn my-tool) test-context {:param "value"}))
(println result)
;; Should see: {:content [...] :resources [...]}
```

## Common Issues and Solutions

### Issue 1: Batch Requests Failing
**Symptom:** Array of requests returns error
**Solution:** This is expected - batch requests are no longer supported. Send requests individually.

### Issue 2: Missing Titles in UI
**Symptom:** Tools/resources show technical names instead of friendly names
**Solution:** Add `title` fields to all your tools, resources, and prompts

### Issue 3: Tool Results Not Displaying Correctly
**Symptom:** Tool outputs appear as raw data structures
**Solution:** Ensure tools return proper structure with `:content` field:
```clojure
{:content [{:type "text" :text "your output"}]}
```

### Issue 4: Completion Context Not Available
**Symptom:** Completion handlers don't receive context
**Solution:** Check that the client supports 2025-06-18 and is sending context

## Backward Compatibility

The MCP Toolkit maintains backward compatibility:

1. **Protocol Negotiation:** Servers automatically support both 2025-03-26 and 2025-06-18
2. **Simple Returns:** Tools can still return simple strings/values (auto-wrapped)
3. **Optional Fields:** All new fields (title, outputSchema, etc.) are optional
4. **Graceful Degradation:** Features like resource links are ignored by older clients

## Example: Complete Migration

Here's a complete before/after example:

### Before (2025-03-26):
```clojure
(ns my-mcp-server
  (:require [mcp-toolkit.server :as server]))

(def file-reader-tool
  {:name "read_file"
   :description "Reads a file from disk"
   :inputSchema {:type "object"
                 :properties {:path {:type "string"}}
                 :required [:path]}
   :tool-fn (fn [context arguments]
              (slurp (:path arguments)))})

(def session
  (atom (server/create-session 
          {:tools [file-reader-tool]})))
```

### After (2025-06-18):
```clojure
(ns my-mcp-server
  (:require [mcp-toolkit.server :as server]
            [mcp-toolkit.impl.meta-support :as meta]))

(def file-reader-tool
  {:name "read_file"
   :title "File Reader"  ;; NEW: Human-readable name
   :description "Reads a file from disk and returns its contents"
   :inputSchema {:type "object"
                 :properties {:path {:type "string"
                                     :description "Path to the file"}}
                 :required [:path]}
   ;; NEW: Define output structure
   :outputSchema {:type "object"
                  :properties {:content {:type "string"}
                               :size {:type "integer"}
                               :path {:type "string"}}}
   :tool-fn (fn [context arguments]
              (let [path (:path arguments)
                    content (slurp path)
                    size (count content)]
                ;; Return structured response with resources
                (-> {:content [{:type "text"
                               :text content}]
                     :resources [{:uri (str "file://" path)
                                 :name (last (clojure.string/split path #"/"))
                                 :mimeType "text/plain"}]}
                    ;; Add metadata
                    (meta/with-meta-field {:read-at (System/currentTimeMillis)
                                          :size-bytes size}))))})

(def session
  (atom (server/create-session 
          {:tools [file-reader-tool]
           ;; Server automatically supports both protocol versions
           })))
```

## Getting Help

- Check the [CHANGELOG](CHANGELOG.md) for detailed changes
- Review the [test suite](test/mcp_toolkit/core_test.cljc) for usage examples
- Visit the #mcp-toolkit channel on Clojurians Slack
- Report issues on the [GitHub repository](https://github.com/metosin/mcp-toolkit)

## Summary

The migration to 2025-06-18 is largely backward compatible. The main improvements are:
1. Better UI with title fields
2. Type-safe tool outputs with schemas
3. Resource linking in tool results
4. Enhanced completion context

Start by adding title fields and testing with your existing setup. Then gradually adopt the new features as needed.
