# mcp-toolkit + mcp-injector: Unified Plan

> **Vision**: One MCP library, same API, runs everywhere: JVM, CLJS, Squint, nbb, and Babashka. With Streamable HTTP as the canonical transport. Plugins compose. mcp-injector is both gateway AND server.

---

## Executive Summary

This plan unifies the JB MCP ecosystem into a composable, data-driven architecture:

1. **mcp-toolkit** — the core library: protocol, registry, transports, plugin system
2. **mcp-injector** — the gateway: governance, PII, audit, virtual models — also usable as an MCP plugin
3. **Server plugins** — each MCP server (art19, podhome, searxng, pinboard) becomes a portable plugin that runs standalone OR loads into a unified server

**Why this matters**: Currently, each MCP server reinvents transport boilerplate (~300-400 lines of copy-paste). mcp-injector connects to N separate endpoints. With this architecture:
- New servers are 50-100 lines of tool definitions + dispatch
- mcp-injector talks to ONE unified endpoint (or loads plugins in-process)
- mcp-injector's governance/PII/audit becomes available to ANY server that loads it as a plugin
- Everything is data-driven, Malli-validated, and composable

---

## Table of Contents

1. [Design Principles](#1-design-principles)
2. [Background: The Current Ecosystem](#2-background-the-current-ecosystem)
3. [Architecture: Data Model](#3-architecture-data-model)
4. [Phase 1: Core Data Model & Registry](#4-phase-1-core-data-model--registry)
5. [Phase 2: Streamable HTTP Transport](#5-phase-2-streamable-http-transport)
6. [Phase 3: Babashka Support](#6-phase-3-babashka-support)
7. [Phase 4: Squint Support](#7-phase-4-squint-support)
8. [Phase 5: Plugin Migration](#8-phase-5-plugin-migration)
9. [Phase 6: mcp-injector Integration](#9-phase-6-mcp-injector-integration)
10. [mcp-injector as MCP Server](#10-mcp-injector-as-mcp-server)
11. [Cross-Cutting Concerns](#11-cross-cutting-concerns)
12. [Architecture Decisions](#12-architecture-decisions)
13. [The Path Forward](#13-the-path-forward)

---

## 1. Design Principles

### Rich Hickey — Simple Made Easy

**Simple** = one role, not intertwined. **Easy** = familiar, nearby.

The current servers are *easy* (copy-paste) but not *simple* (transport + protocol + tool logic are intertwined). We separate concerns into orthogonal, composable pieces:

| Concern | What it does | Represented as |
|---------|-------------|----------------|
| Transport | How bytes move | `mcp-toolkit.transport/*` |
| Protocol | JSON-RPC message routing | `mcp-toolkit.json-rpc` |
| Registry | What the server offers | `mcp-toolkit.registry` (data) |
| Handlers | What the server *does* | `:handler` fn on each tool |
| Config | How it's parameterized | Data maps, Malli-validated |

Each piece is **data**, not code. Each is **composable**.

### Eric Normand — Data Modeling

**Bad data models create code complexity.** Count the valid states in your domain vs. the states your model can represent. The gap is complexity debt.

The current model splits tools into two places:
```clojure
;; Schema in one place
(def tools [{:name "x" :description "y" :inputSchema {...}}])

;; Handler in another, matched by string name
(defn dispatch [name args]
  (case name "x" (handle-x args)))
```

This creates impossible states: handler with no schema, schema with no handler, name collisions, typos in string names.

**Better model**: a tool is ONE value carrying both:
```clojure
{:name :art19/list-episodes        ;; namespaced keyword internally
 :description "List episodes..."
 :inputSchema {...}
 :handler (fn [context args] ...)}
```
One value. One source of truth. No name-matching gaps. Namespaced keywords give us Clojure's namespace semantics for free — no fragile `__` separators.

### Hillel Wayne — Properties & Invariants

Think about what must **always** be true, not just examples:

1. **Registry consistency**: `(= (count (all-tools reg)) (sum (map count (map :tools (vals reg)))))` — no duplicates, no orphans
2. **Name uniqueness**: every tool name is unique across all plugins
3. **Handler totality**: every registered tool has a non-nil `:handler`
4. **Schema validity**: every `:inputSchema` is valid JSON Schema
5. **Result validity**: every handler returns `{content [...]}` or `{content [...] :isError true}`
6. **Index consistency**: `(= (count (index reg)) (count (all-tools reg)))` — the O(1) index always matches the tool set

We design the data model so these are **structurally guaranteed**, not just tested. Invariants are enforced at registration boundaries (inside `swap!`), never deferred to enumeration.

### Malli — Data-Driven Schemas

Metosin already maintains mcp-toolkit. Malli's philosophy: **schemas are data, transformations are data, validation is data**. Everything is composable vectors and maps.

Our tool registry, server config, transport config, and plugin definitions should all be **Malli-validateable data**. This gives us:
- Dev-time error messages with hints
- Runtime validation at boundaries
- Auto-generated docs from schemas
- Test data generation
- Static type linting

### State of the Art Context

The MCP ecosystem has several aggregation patterns (1MCP, AgentGateway, Armory, Nexus-MCP) but **all are proxies** — they forward to separate HTTP endpoints. None have in-process plugin composition. In the Clojure space, three MCP libraries exist (metosin/mcp-toolkit, Latacora mcp-sdk, Gaiwan mcp-sdk) — none have a plugin registry.

Our approach is novel: **in-process plugin composition** eliminates the network hop entirely. This is closer to a module system than a proxy.

The security community has identified tool name collisions as a CVE-class vulnerability (CVE-2026-30856). Our namespaced keyword approach + registration-time collision detection addresses this structurally.

---

## 2. Background: The Current Ecosystem

### Existing MCP Servers in the JB Stack

| Server         | Runtime  | Transport       | Lines | Notes                      |
| -------------- | -------- | --------------- | ----- | -------------------------- |
| `art19-mcp`    | Babashka | Streamable HTTP | ~1100 | Reference implementation   |
| `podhome-mcp`  | Babashka | Streamable HTTP | ~775  | Based on art19-mcp pattern |
| `pinboard-mcp` | Babashka | Streamable HTTP | ~463  | Bookmark API               |
| `searxng-mcp`  | ?        | ?               | ?     | Search metasearch          |
| `hedgedoc-mcp` | Squint   | STDIO           | ~300  | MCP over Socket.IO         |

### The Problem: Duplicate Transport Logic

Every server re-implements the same patterns:

```
┌─────────────────────────────────────────────────────────────┐
│ art19_mcp.bb (1114 lines)                                  │
│ ├─ Session management (atom, UUIDs)                       │
│ ├─ JSON-RPC dispatch (initialize, tools/list, tools/call)   │
│ ├─ HTTP server (httpkit run-server)                        │
│ └─ Tool definitions + dispatch                             │
├─────────────────────────────────────────────────────────────┤
│ podhome_mcp.clj (~775 lines)                              │
│ ├─ Same session management (copy-pasted)                   │
│ ├─ Same JSON-RPC dispatch (copy-pasted)                   │
│ ├─ Same HTTP server (copy-pasted)                         │
│ └─ Tool definitions + dispatch                             │
├─────────────────────────────────────────────────────────────┤
│ pinboard_mcp.bb (~463 lines)                              │
│ ├─ Same session management (copy-pasted)                   │
│ ├─ Same JSON-RPC dispatch (copy-pasted)                   │
│ ├─ Same HTTP server (copy-pasted)                         │
│ └─ Tool definitions + dispatch                             │
└─────────────────────────────────────────────────────────────┘
```

### mcp-injector: The Consumer (and Future Server)

`mcp-injector` currently consumes MCP servers as tools:

- **HTTP transport**: Connects to `http://host:port/mcp`, handles session IDs
- **STDIO transport**: Spawns subprocess, reads/writes JSON-RPC on stdin/stdout
- **Protocol version**: Uses `2025-06-18`
- **Header handling**: Sends `MCP-Protocol-Version`, `MCP-Session-Id`, `Accept: application/json, text/event-stream`

But mcp-injector also has capabilities that **other servers could use**:
- Governance framework (permissive/strict tool policies)
- PII scanning & restoration
- Signed audit trail
- Virtual model chains with provider fallback
- Error translation

These could be exposed as MCP tools — making mcp-injector both gateway AND server.

---

## 3. Architecture: Data Model

### The Core Data Model

```
                    ┌─────────────────────────────────────────┐
                    │           mcp-toolkit                    │
                    │                                          │
                    │  ┌───────────┐  ┌──────────────────────┐ │
                    │  │  Registry │  │     Transport        │ │
                    │  │           │  │                      │ │
                    │  │ tools     │  │  streamable-http     │ │
                    │  │ prompts   │◄─┤  stdio               │ │
                    │  │ resources │  │  (future: websocket) │ │
                    │  └─────┬─────┘  └──────────┬───────────┘ │
                    │        │                   │             │
                    │  ┌─────▼───────────────────▼───────────┐ │
                    │  │         Protocol (JSON-RPC)         │ │
                    │  │                                     │ │
                    │  │  handle-message → route → respond   │ │
                    │  └─────────────────────────────────────┘ │
                    └─────────────────────────────────────────┘
                              ▲           ▲
                    ┌─────────┘           └──────────┐
                    │                                │
         ┌──────────▼──────────┐       ┌────────────▼────────────┐
         │  Unified Server     │       │  Standalone Server      │
         │  (multi-plugin)     │       │  (single plugin)        │
         │                     │       │                         │
         │  (register! :art19  │       │  (mcp/create-server     │
         │   art19/plugin)     │       │    art19/plugin)        │
         │  (register! :podhome│       │                         │
         │   podhome/plugin)   │       │  Same plugin data,      │
         │  (register! :searxng│       │  different entry point) │
         │   searxng/plugin)   │       │                         │
         └─────────────────────┘       └─────────────────────────┘
```

### A Tool Is One Value

Tools use **namespaced keywords** internally (`:art19/list-episodes`). The MCP protocol boundary serializes to string (`"art19__list_episodes`). This gives Clojure namespace semantics for free and prevents separator collisions.

```clojure
;; Schema + handler together — no split, no name matching
(defn tool [schema handler-fn]
  {:name (:name schema)         ;; keyword, e.g. :art19/list-episodes
   :description (:description schema)
   :inputSchema (:inputSchema schema)
   :handler handler-fn})

;; Serialize to MCP protocol format
(defn tool->protocol [tool]
  {:name (munge-name (:name tool))  ;; :art19/list-episodes → "art19__list_episodes"
   :description (:description tool)
   :inputSchema (:inputSchema tool)})

;; Deserialize from MCP protocol
(defn protocol->tool-name [s]
  ;; "art19__list_episodes" → :art19/list-episodes
  (let [[ns name] (str/split s #"__" 2)]
    (keyword ns name)))
```

### A Plugin Is One Value

```clojure
;; Malli schema for plugins
(def plugin-schema
  [:map {:closed true}
   [:name :keyword]                            ;; e.g. :art19
   [:version :string]                          ;; e.g. "1.0.0"
   [:tools [:vector tool-schema]]              ;; vector of tool maps
   [:prompts [:optional [:vector :any]]]
   [:resources [:optional [:vector :any]]]
   [:dependencies [:optional [:vector :keyword]]]  ;; other plugins this needs
   [:lifecycle [:optional lifecycle-schema]]
   [:config [:optional [:fn malli-schema?]]]]) ;; must be a valid Malli schema

;; Tool schema — namespaced keyword name, handler required
(def tool-schema
  [:map {:closed true}
   [:name :keyword]                            ;; :art19/list-episodes
   [:description :string]
   [:inputSchema :map]
   [:handler fn?]
   [:timeout [:optional :int]]                 ;; per-tool timeout in ms
   [:dependencies [:optional [:vector :keyword]]]]) ;; tools this depends on

;; Plugin lifecycle hooks
(def lifecycle-schema
  [:map {:closed true}
   [:on-register [:optional fn?]]
   [:on-unregister [:optional fn?]]
   [:on-error [:optional fn?]]])

;; Example plugin
(def plugin
  {:name :art19
   :version "1.0.0"
   :tools [
     {:name :art19/list-episodes
      :description "List episodes for a series"
      :inputSchema {:type "object" :properties {...}}
      :handler (fn [context args] ...)}]
   :dependencies [:injector]  ;; needs injector for PII scanning
   :lifecycle {:on-register (fn [] (init-connection))
               :on-unregister (fn [] (close-connection))}
   :config [:map
            [:api-token :string]
            [:api-credential :string]]})
```

### A Registry Is a Map with Derived Index

The registry stores plugins AND maintains a derived `tool-index` map for O(1) handler lookup. The index is built inside the `swap!` transaction so it's always consistent.

```clojure
;; Registry internal structure
{:plugins {:art19 {...} :podhome {...}}   ;; plugin-name → plugin
 :tool-index {:art19/list-episodes {:handler ... :plugin :art19}
              :podhome/get-show {:handler ... :plugin :podhome}}}

(defn create
  "Create an empty registry."
  []
  (atom {:plugins {} :tool-index {}}))

(defn register!
  "Register a plugin. Validates schema, checks tool collisions, builds index — all atomically."
  [registry plugin]
  (let [pname (:name plugin)]
    ;; Validate before entering swap!
    (when-let [errors (m/explain plugin-schema plugin)]
      (throw (ex-info "Invalid plugin"
                      {:plugin pname
                       :errors (me/humanize errors)})))
    ;; Atomic: check plugin collision + tool collisions + build index
    (swap! registry
           (fn [{:keys [plugins tool-index] :as state}]
             (when (contains? plugins pname)
               (throw (ex-info (str "Plugin already registered: " pname)
                               {:plugin pname})))
             ;; Check tool name collisions against ALL existing tools
             (let [existing-tools (set (keys tool-index))
                   new-tools (set (map :name (:tools plugin)))
                   collisions (clojure.set/intersection existing-tools new-tools)]
               (when (seq collisions)
                 (throw (ex-info (str "Duplicate tool names: " (str/join ", " collisions))
                                 {:collisions collisions
                                  :plugin pname}))))
             ;; Build new index entries
             (let [new-index (reduce (fn [idx tool]
                                       (assoc idx (:name tool)
                                              {:handler (:handler tool)
                                               :plugin pname
                                               :timeout (:timeout tool)
                                               :dependencies (:dependencies tool)}))
                                     tool-index
                                     (:tools plugin))]
               {:plugins (assoc plugins pname plugin)
                :tool-index new-index})))
    ;; Run lifecycle hook
    (when-let [hook (get-in plugin [:lifecycle :on-register])]
      (hook))
    registry))

(defn unregister!
  "Remove a plugin. Drains in-flight calls, runs lifecycle, removes from index."
  [registry plugin-name]
  (swap! registry
         (fn [{:keys [plugins tool-index] :as state}]
           (when-not (contains? plugins plugin-name)
             (throw (ex-info (str "Plugin not registered: " plugin-name)
                             {:plugin plugin-name})))
           ;; Remove this plugin's tools from index
           (let [plugin-tools (->> tool-index
                                   (filter (fn [[_ v]] (= (:plugin v) plugin-name)))
                                   (map key)
                                   set)
                 new-index (apply dissoc tool-index plugin-tools)]
             {:plugins (dissoc plugins plugin-name)
              :tool-index new-index})))
  ;; Run lifecycle hook
  (when-let [hook (get-in (get @registry [:plugins plugin-name]) [:lifecycle :on-unregister])]
    (hook))
  registry)

;; O(1) handler lookup
(defn find-tool-handler
  "Find a tool by name and return its handler + metadata.
   Returns nil if not found. O(1) via index."
  [registry tool-name]
  (get-in @registry [:tool-index tool-name]))
```

### Server Config Is One Value

```clojure
(def server-config-schema
  [:map {:closed true}
   [:registry :any]                      ;; the registry atom
   [:server-info
    [:map {:closed true}
     [:name :string]
     [:version :string]]]
   [:transport
    [:map {:closed true}
     [:type [:enum :http :stdio]]
     [:port [:optional :int]]
     [:host [:optional :string]]
     [:allowed-origins [:optional [:vector :string]]]]]
   [:observability [:optional observability-schema]]])
```

---

## 4. Phase 1: Core Data Model & Registry

### New Files

```
src/mcp_toolkit/
├── registry.cljc          ;; NEW: plugin registry, merge, validation, O(1) index
├── server.cljc            ;; EXTEND: accept plugin/registry config
├── json_rpc.cljc          ;; (unchanged)
├── client.cljc            ;; (unchanged)
├── impl/
│   ├── common.cljc        ;; EXTEND: tool name munge/unmunge
│   ├── server/
│   │   └── handler.cljc   ;; EXTEND: route tool calls through registry index
│   └── client/
│       └── handler.cljc   ;; (unchanged)
└── transport/
    └── streamable_http.clj ;; NEW: Phase 2
```

### `mcp-toolkit.registry`

Full implementation shown in Section 3 above. Key design points:

- **Collision detection inside `swap!`** — invariant enforced at registration, not deferred
- **Derived index map** — O(1) handler lookup, always consistent with plugins
- **Namespaced keywords** — `:art19/list-episodes` internally, `"art19__list_episodes"` at protocol boundary
- **Lifecycle hooks** — `:on-register`, `:on-unregister`, `:on-error`
- **Dependency declarations** — plugins declare which other plugins they need
- **Config schema validation** — `:config` must be a valid Malli schema (`[:fn malli-schema?]`)

### `mcp-toolkit.impl.common` — Name Munging

```clojure
(ns mcp-toolkit.impl.common)

(defn munge-name
  "Convert a namespaced keyword to MCP protocol string.
   :art19/list-episodes → \"art19__list_episodes\""
  [k]
  (str (namespace k) "__" (name k)))

(defn unmunge-name
  "Convert MCP protocol string to namespaced keyword.
   \"art19__list_episodes\" → :art19/list-episodes"
  [s]
  (let [[ns name] (str/split s #"__" 2)]
    (keyword ns name)))
```

### Updated `server.cljc` — Accept Plugin Config

The existing `create-session` takes `:tools`, `:prompts`, `:resources` as separate vectors. We add a `:registry` option that merges them:

```clojure
(defn create-session
  [{:keys [registry server-info ...] :as config}]
  (let [tools (if registry
                (->> @registry :tool-index
                     (map (fn [[name {:keys [handler plugin]}]]
                            (let [tool (->> (get-in @registry [:plugins plugin :tools])
                                            (some #(when (= (:name %) name)) %))]
                              (assoc (select-keys tool [:name :description :inputSchema])
                                     :name (munge-name (:name tool))))))
                     vec)
                (:tools config))
        prompts (if registry
                  (registry/all-prompts registry)
                  (:prompts config))
        resources (if registry
                    (registry/all-resources registry)
                    (:resources config))]
    ;; ... existing create-session logic with merged data
    ))
```

### Updated `handler.cljc` — Route Through Registry Index

```clojure
(defn tool-call-handler [{:keys [session message] :as context}]
  (let [{:keys [name arguments]} (:params message)
        tool-name (unmunge-name name)]
    (if-some [{:keys [handler plugin timeout]} (registry/find-tool-handler @session :registry tool-name)]
      (let [context (assoc context :plugin plugin :request-id (:id message))]
        (-> (if timeout
              (p/timeout (handler context arguments) timeout)
              (handler context arguments))
            (p/catch (fn [exception]
                       {:content [{:type "text"
                                   :text (ex-message exception)}]
                        :isError true}))))
      (json-rpc/invalid-tool-name (:id message) name))))
```

---

## 5. Phase 2: Streamable HTTP Transport

### Spec Reference

The Streamable HTTP transport is defined in the [MCP spec 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports):

| Feature                | Spec Requirement         | Implementation               |
| ---------------------- | ------------------------ | ---------------------------- |
| `POST /mcp`            | Client→server messages   | ✅ (art19-mcp has this)      |
| `GET /mcp`             | Server→client SSE stream | ❌ Missing                   |
| `DELETE /mcp`          | Session termination      | ❌ Missing                   |
| `MCP-Session-Id`       | Session management       | ✅ (art19-mcp has this)      |
| `MCP-Protocol-Version` | Version header           | ✅ (mcp-injector sends this) |
| `Origin` validation    | Security (MUST)          | ❌ Missing                   |
| `Last-Event-ID`        | Resumability (SHOULD)    | ❌ Missing                   |

### New File: `src/mcp_toolkit/transport/streamable_http.clj`

Extracted from the art19-mcp/podhome-mcp/pinboard-mcp patterns. Key differences from the draft in the old plan:

- Session management is **parameterized** (not global atom) — each server instance gets its own
- The handler takes a **dispatch function** from mcp-toolkit, not raw JSON-RPC
- Origin validation is **configurable**
- Works with the registry: dispatch routes through `registry/find-tool-handler`

```clojure
(ns mcp-toolkit.transport.streamable-http
  "Streamable HTTP transport for MCP servers.
   Implements the 2025-11-25 spec."
  (:require [org.httpkit.server :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [mcp-toolkit.json-rpc :as json-rpc]))

;; Session management is per-server-instance, not global
(defn create-session-store []
  (atom {}))

(defn create-session! [store]
  (let [sid (str (java.util.UUID/randomUUID))]
    (swap! store assoc sid {:created-at (System/currentTimeMillis)})
    sid))

(defn valid-session? [store sid]
  (boolean (and sid (contains? @store sid))))

(defn delete-session! [store sid]
  (swap! store dissoc sid))

;; HTTP helpers
(defn- header [request name] ...)
(defn- json-response [status body & [headers]] ...)
(defn- parse-body [request] ...)

;; Request handlers — take dispatch-fn from mcp-toolkit
(defn- handle-post [request dispatch-fn session-store] ...)
(defn- handle-get [request session-store] ...)  ;; SSE stream
(defn- handle-delete [request session-store] ...)

;; Public API
(defn create-handler
  "Create an http-kit handler function.
   dispatch-fn: (fn [json-rpc-message session-id] response)"
  [dispatch-fn & [{:keys [session-store allowed-origins]
                    :or {session-store (create-session-store)}}]]
  (fn [request]
    (let [uri (:uri request)]
      (cond
        (= uri "/mcp")
        (case (:request-method request)
          :post (handle-post request dispatch-fn session-store)
          :get (handle-get request session-store)
          :delete (handle-delete request session-store)
          {:status 405 :body "Method Not Allowed"})

        (= uri "/health")
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string {:status "ok"})}

        :else
        {:status 404 :body "Not Found"}))))

(defn run-server
  "Start an MCP server with Streamable HTTP transport.
   server: result of mcp-toolkit.server/create-session or similar
   opts: {:port 3000 :host \"127.0.0.1\"}"
  [server & [{:keys [port host]
              :or {port 0 host "127.0.0.1"}}]]
  (let [session-store (create-session-store)
        dispatch-fn (make-dispatch-fn server session-store)
        handler (create-handler dispatch-fn {:session-store session-store})]
    (http/run-server handler {:port port :host host})))
```

### Example: `example/server-http/`

```
example/server-http/
├── server.clj      ;; Main server using streamable-http transport
├── deps.edn        ;; Dependencies (mcp-toolkit, http-kit, cheshire)
└── README.md       ;; Usage instructions
```

---

## 6. Phase 3: Babashka Support

### Current Status

The README says `[ ] Babashka` is unchecked, but the library should work because:

1. **Promesa works in Babashka** — `promesa.core` is just Clojure
2. **http-kit works in Babashka** — bundled in bb
3. **CLJC files work** — `#?(:clj ...)` reader conditionals work in bb

### Verification Steps

1. Run existing tests under bb
2. Check what breaks (if anything)
3. Document any required changes

### Expected Outcome

Babashka support should "just work" with minimal or no changes. Primary value is **documenting** it works and adding a Babashka example.

---

## 7. Phase 4: Squint Support

### The Challenge: Native Promises

Squint compiles ClojureScript to native JavaScript. It uses native `js/Promise`, not promesa.

**The problem**: `mcp-toolkit` uses `promesa` functions throughout:
- `p/create`, `p/then`, `p/catch`, `p/handle`, `p/all`

### The Solution: Internal Promise Shim

Create `src/mcp_toolkit/impl/promise.cljc`:

```clojure
(ns mcp-toolkit.impl.promise
  "Internal promise abstraction.
   Dispatches to promesa on :clj/:cljs, native js/Promise on :squint."
  #?(:clj  (:require [promesa.core :as p])
     :cljs (:require [promesa.core :as p])))

(defn create [f]
  #?(:squint  (js/Promise. f)
     :default (p/create f)))

(defn then
  ([p f]
   #?(:squint  (.then p f)
      :default (p/then p f)))
  ([p f g]
   #?(:squint  (.then p f g)
      :default (p/then p f g))))

(defn catch- [p f]
  #?(:squint  (.catch p f)
     :default (p/catch p f)))

(defn handle [p f]
  #?(:squint  (.then p (fn [v] (f v nil)) (fn [e] (f nil e)))
     :default (p/handle p f)))

(defn all [ps]
  #?(:squint  (.then (js/Promise.all (to-array ps))
                     (fn [arr] (vec (array-seq arr))))
     :default (p/all ps)))
```

Swap `[promesa.core :as p]` → `[mcp-toolkit.impl.promise :as p]` in:
- `src/mcp_toolkit/json_rpc.cljc`
- `src/mcp_toolkit/server.cljc`
- `src/mcp_toolkit/client.cljc`
- `src/mcp_toolkit/impl/server/handler.cljc`

**Zero call-site changes.** See `~/src/vamp/mcp-toolkit-squint-compat-spec.md` for the full spec.

### Fork vs PR Strategy

The promise shim changes core files. Decision tree:

1. **First**: Open a PR to metosin/mcp-toolkit with just the shim (non-breaking, adds a new namespace)
2. **If accepted**: Use upstream
3. **If not merged within 2 weeks**: Fork under personal/org account, maintain parallel to upstream
4. **Fork strategy**: Track upstream main, rebase fork on top, only add our changes (shim + registry + transport)

The registry and transport are additive — unlikely to conflict with upstream. The shim is the riskiest part.

---

## 8. Phase 5: Plugin Migration

### Migration Path for Existing Servers

Each server becomes a plugin module that exports `def plugin` and can run standalone.

| Server         | Current          | Target                              | Effort   |
| -------------- | ---------------- | ----------------------------------- | -------- |
| `art19-mcp`    | Hand-rolled HTTP | Plugin + standalone via mcp-toolkit | ~3 hours |
| `podhome-mcp`  | Hand-rolled HTTP | Plugin + standalone via mcp-toolkit | ~2 hours |
| `pinboard-mcp` | Hand-rolled HTTP | Plugin + standalone via mcp-toolkit | ~2 hours |
| `searxng-mcp`  | ?                | Plugin + standalone via mcp-toolkit | ~2 hours |
| `hedgedoc-mcp` | Squint + stdio   | Plugin (squint)                     | ~4 hours |

### Pinboard Migration (Smallest — Proof of Concept)

```clojure
;; pinboard_mcp.bb — after migration
#!/usr/bin/env bb
(ns pinboard-mcp
  (:require [mcp-toolkit.server :as mcp]
            [mcp-toolkit.transport.streamable-http :as http]
            [mcp-toolkit.registry :as reg]
            [babashka.http-client :as http-client]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; ─── Tool Definitions ────────────────────────────────────────────────────

(defn- list-bookmarks [config args] ...)
(defn- search-bookmarks [config args] ...)
(defn- add-bookmark [config args] ...)
(defn- delete-bookmark [config args] ...)
(defn- list-tags [config _] ...)
(defn- recent-bookmarks [config args] ...)

(defn- make-tools [config]
  [{:name :pinboard/list-bookmarks
    :description "List all bookmarks. Filter by tag, limit results."
    :inputSchema {:type "object"
                  :properties {:tag {:type "string"}
                               :limit {:type "integer"}}
                  :required []}
    :handler (fn [_ args] (list-bookmarks config args))}

   {:name :pinboard/search-bookmarks
    :description "Full-text search across title, description, and tags."
    :inputSchema {:type "object"
                  :properties {:query {:type "string"}
                               :limit {:type "integer"}}
                  :required ["query"]}
    :handler (fn [_ args] (search-bookmarks config args))}

   ;; ... more tools
   ])

;; ─── Plugin Export ───────────────────────────────────────────────────────

(defn create-plugin [config]
  {:name :pinboard
   :version "1.0.0"
   :tools (make-tools config)
   :config [:map
            [:token :string]
            [:endpoint [:optional :string]]]})

;; ─── Standalone Entry Point ──────────────────────────────────────────────

(defn -main [& _args]
  (let [config (get-pinboard-config)
        plugin (create-plugin config)
        registry (-> (reg/create)
                     (reg/register! plugin))
        server (mcp/create-session
                {:registry registry
                 :server-info {:name "pinboard-mcp" :version "1.0.0"}})
        port (parse-port (System/getenv "PINBOARD_MCP_PORT"))
        srv (http/run-server server {:port port})]
    (println (json/generate-string
              {:status "started"
               :server "pinboard-mcp"
               :port (:local-port (meta srv))
               :url (str "http://127.0.0.1:" (:local-port (meta srv)) "/mcp")
               :tools (count (:tools plugin))}))
    @(promise)))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
```

### Unified Server Example

```clojure
;; unified_server.clj
(ns unified-server
  (:require [mcp-toolkit.server :as mcp]
            [mcp-toolkit.transport.streamable-http :as http]
            [mcp-toolkit.registry :as reg]
            [pinboard-mcp :as pinboard]
            [art19-mcp :as art19]
            [podhome-mcp :as podhome]
            [searxng-mcp :as searxng]))

(defn -main [& args]
  (let [registry (-> (reg/create)
                     (reg/register! (pinboard/create-plugin pinboard-config))
                     (reg/register! (art19/create-plugin art19-config))
                     (reg/register! (podhome/create-plugin podhome-config))
                     (reg/register! (searxng/create-plugin searxng-config)))
        server (mcp/create-session
                {:registry registry
                 :server-info {:name "jb-mcp" :version "1.0.0"}})]
    (http/run-server server {:port 3000})))
```

---

## 9. Phase 6: mcp-injector Integration

### Three Modes for mcp-injector

#### Mode 1: Remote (Current — Unchanged)

mcp-injector connects to N separate MCP servers over HTTP/STDIO. Works as-is.

```clojure
;; mcp-servers.edn
{:servers
  {:art19    {:url "http://localhost:3001/mcp"}
   :podhome  {:url "http://localhost:3002/mcp"}
   :pinboard {:url "http://localhost:3003/mcp"}
   :searxng  {:url "http://localhost:3004/mcp"}}}
```

#### Mode 2: Unified Remote (New)

mcp-injector connects to ONE unified mcp-toolkit server that hosts all plugins.

```clojure
;; mcp-servers.edn — simplified
{:servers
  {:jb-unified
    {:url "http://localhost:3000/mcp"
     :trust :restore
     :tools ["*"]}}}
```

Benefits:
- One connection, one session, one audit trail
- Tool names are namespaced by plugin (e.g., `art19__list_episodes`)
- Governance policies target plugin-prefixed tool names

#### Mode 3: In-Process Plugin (New)

mcp-injector loads mcp-toolkit plugins directly — no HTTP hop, no subprocess.

```clojure
;; mcp-servers.edn — in-process
{:plugins
  [{:require pinboard-mcp
    :as pinboard
    :config {:token "..."}}
   {:require art19-mcp
    :as art19
    :config {:api-token "..." :api-credential "..."}}]}
```

mcp-injector creates a registry, registers the plugins, and routes tool calls through `registry/find-tool-handler`. Governance, PII, and audit still apply.

### mcp-injector Code Changes

Add a plugin loading path to `mcp_injector/core.clj`:

```clojure
(defn load-plugins [plugin-configs]
  (let [registry (reg/create)]
    (reduce (fn [reg {:keys [require as config]}]
              (let [plugin-ns (requiring-resolve require)
                    plugin ((requiring-resolve (symbol (name as) "create-plugin")) config)]
                (reg/register! reg plugin)))
            registry
            plugin-configs)))

(defn create-plugin-dispatch [registry]
  (fn [tool-name args]
    (when-let [{:keys [handler]} (reg/find-tool-handler registry (unmunge-name tool-name))]
      (handler nil args))))  ;; nil context for in-process mode
```

### Tool Name Namespacing

Internal names are namespaced keywords. At the MCP protocol boundary, they become strings:

```
:art19/list-episodes      → "art19__list_episodes"
:podhome/get-show         → "podhome__get-show"
:pinboard/list-bookmarks  → "pinboard__list_bookmarks"
:searxng/search           → "searxng__search"
```

mcp-injector's governance policies target these namespaced names:

```clojure
:governance
{:mode :permissive
 :policy
  {:allow ["art19__*" "podhome__*" "searxng__*"]
   :deny ["pinboard__delete_bookmark"]}}
```

---

## 10. mcp-injector as MCP Server

### The Idea

mcp-injector's capabilities — governance, PII scanning, audit, virtual models — are themselves useful as MCP tools. Other servers or agents could call them.

### Security Model for Admin Tools

The governance/PII/audit tools are **privileged**. They are NOT available to all connected clients. Access control options:

1. **Separate admin endpoint** — `/admin/mcp` with its own session store and auth
2. **Capability tokens** — each privileged tool requires a token in the call arguments
3. **Session-bound roles** — sessions are tagged as `:admin` or `:standard` at connection time

**Recommended**: Option 1 (separate endpoint) + Option 3 (session roles). The admin endpoint requires a separate API key. Standard sessions cannot see or call admin tools.

```clojure
;; Admin plugin — only available on the admin endpoint
(defn create-admin-plugin [injector-config]
  {:name :injector-admin
   :version "1.0.0"
   :tools [
     {:name :injector/restore-pii
      :description "Restore PII tokens to original values"
      :inputSchema {:type "object"
                    :properties {:request_id {:type "string"}
                                 :text {:type "string"}}
                    :required ["request_id" "text"]}
      :handler (fn [_ args] ...)}

     {:name :injector/query-audit
      :description "Query the audit log"
      :inputSchema {:type "object"
                    :properties {:limit {:type "integer"}}
                    :required []}
      :handler (fn [_ args] ...)}

     {:name :injector/check-tool-access
      :description "Check if a tool call is allowed"
      :inputSchema {:type "object"
                    :properties {:tool_name {:type "string"}}
                    :required ["tool_name"]}
      :handler (fn [_ args] ...)}]}

;; Standard plugin — available to all sessions
(defn create-standard-plugin [injector-config]
  {:name :injector
   :version "1.0.0"
   :tools [
     {:name :injector/scan-pii
      :description "Scan text for PII and return redacted version"
      :inputSchema {:type "object"
                    :properties {:text {:type "string"}}
                    :required ["text"]}
      :handler (fn [_ args] ...)}

     {:name :injector/get-stats
      :description "Get usage statistics"
      :inputSchema {:type "object" :properties {} :required []}
      :handler (fn [_ _] ...)}

     {:name :injector/route-prompt
      :description "Route a prompt through a virtual model chain"
      :inputSchema {:type "object"
                    :properties {:virtual_model {:type "string"}
                                 :messages {:type "array"}}
                    :required ["virtual_model" "messages"]}
      :handler (fn [_ args] ...)}]})
```

### Usage: Injector as a Plugin in Unified Server

```clojure
;; unified_server.clj — with injector as a plugin
(ns unified-server
  (:require [mcp-toolkit.server :as mcp]
            [mcp-toolkit.transport.streamable-http :as http]
            [mcp-toolkit.registry :as reg]
            [art19-mcp :as art19]
            [podhome-mcp :as podhome]
            [mcp-injector.plugin :as injector]))

(defn -main [& args]
  (let [registry (-> (reg/create)
                     (reg/register! (injector/create-standard-plugin injector-config))
                     (reg/register! (art19/create-plugin art19-config))
                     (reg/register! (podhome/create-plugin podhome-config)))
        admin-registry (-> (reg/create)
                           (reg/register! (injector/create-admin-plugin injector-config)))
        server (mcp/create-session
                {:registry registry
                 :server-info {:name "jb-mcp" :version "1.0.0"}})
        admin-server (mcp/create-session
                      {:registry admin-registry
                       :server-info {:name "jb-mcp-admin" :version "1.0.0"}})]
    ;; Standard endpoint
    (http/run-server server {:port 3000})
    ;; Admin endpoint (separate port, requires admin API key)
    (http/run-server admin-server {:port 3001})))
```

Now an LLM agent can call:
- `injector__scan_pii` — check if output contains sensitive data
- `injector__get_stats` — review usage statistics
- `injector__route_prompt` — send a prompt through a virtual model chain

Admin tools (separate endpoint, admin auth required):
- `injector-admin__restore-pii` — recover redacted PII
- `injector-admin__query-audit` — review audit log
- `injector-admin__check-tool-access` — verify governance policies

### Usage: Injector as a Standalone Server

```clojure
;; injector_server.clj
(ns injector-server
  (:require [mcp-toolkit.server :as mcp]
            [mcp-toolkit.transport.streamable-http :as http]
            [mcp-toolkit.registry :as reg]
            [mcp-injector.plugin :as injector]))

(defn -main [& args]
  (let [registry (-> (reg/create)
                     (reg/register! (injector/create-standard-plugin injector-config)))
        server (mcp/create-session
                {:registry registry
                 :server-info {:name "mcp-injector" :version "2.0.0"}})]
    (http/run-server server {:port 3100})))
```

mcp-injector keeps its current gateway/proxy role AND becomes an MCP server. Both modes share the same config, audit log, and policy engine.

---

## 11. Cross-Cutting Concerns

### Error Model

Handlers can fail. The registry and transport must handle failures gracefully:

```clojure
;; Error types
(defn handler-error [message & [data]]
  {:type :handler-error
   :message message
   :data (or data {})})

(defn timeout-error [tool-name ms]
  {:type :timeout-error
   :message (str "Tool " tool-name " timed out after " ms "ms")
   :tool-name tool-name
   :timeout-ms ms})

(defn plugin-error [plugin-name message]
  {:type :plugin-error
   :message message
   :plugin plugin-name})

;; Handler wrapper with error handling
(defn wrap-handler [handler tool-name timeout-ms]
  (fn [context args]
    (let [call (if timeout-ms
                 (p/timeout (handler context args) timeout-ms
                            (fn [] (timeout-error tool-name timeout-ms)))
                 (handler context args))]
      (-> call
          (p/catch (fn [e]
                     {:content [{:type "text"
                                 :text (str "Error in " tool-name ": " (ex-message e))}]
                      :isError true
                      :_error (assoc (handler-error (ex-message e))
                                     :tool-name tool-name)}))))))
```

**Error propagation rules**:
- Handler exceptions → MCP error response with `isError: true`
- Timeout exceptions → MCP error response with timeout message
- Plugin crashes during `all-tools` → log error, exclude plugin, continue
- Registry corruption → fail fast, don't serve partial state

### Observability

```clojure
;; Instrumentation hooks — called on every tool call
(defn- instrument-call [registry plugin-name tool-name start-ms result]
  (let [elapsed (- (System/currentTimeMillis) start-ms)
        error? (:isError result)]
    ;; Log
    (log/info "tool-call" {:plugin plugin-name
                           :tool tool-name
                           :elapsed-ms elapsed
                           :error? error?})
    ;; Metrics (if configured)
    (when-let [metrics (:metrics @registry)]
      (metrics/record metrics "tool.call.duration" elapsed
                      {:plugin plugin-name :tool tool-name :error? error?})
      (metrics/increment metrics "tool.call.count"
                         {:plugin plugin-name :tool tool-name :error? error?}))))

;; Context carries observability info
(defn make-context [session plugin-name request-id]
  {:session session
   :plugin plugin-name
   :request-id request-id
   :start-ms (System/currentTimeMillis)
   :instrument (fn [result] (instrument-call ...))})
```

**Minimum observability**:
- Structured log per tool call: plugin, tool, duration, error status
- `/metrics` endpoint (optional): Prometheus-compatible metrics
- Request ID correlation: same ID flows through handler → result → audit log

### Hot-Reload Semantics

When a plugin is unregistered while sessions are active:

1. **In-flight calls complete** — the handler reference is captured at call start, deregistration doesn't kill it
2. **New calls fail** — `find-tool-handler` returns nil for removed tools
3. **Session tool list is NOT cached** — each `tools/list` reads current registry state
4. **Grace period** — `:on-unregister` hook lets plugins flush buffers, close connections

```clojure
;; Session snapshots the tool list at initialization (optional)
(defn create-session
  [{:keys [registry server-info snapshot-tools?] :as config}]
  (let [tools (if snapshot-tools?
                ;; Snapshot: tools are fixed for this session's lifetime
                (registry/all-tools registry)
                ;; Live: tools reflect current registry state
                (registry/all-tools registry))]
    ...))
```

### Cross-Plugin Tool Calling

A tool in one plugin can call a tool in another plugin via the registry:

```clojure
;; pinboard__search-bookmarks calls searxng__search for enriched results
(defn- search-bookmarks [registry config args]
  (let [bookmarks (fetch-bookmarks config args)
        ;; Cross-plugin call
        searxng-handler (get-in @registry [:tool-index :searxng/search :handler])
        enriched (when searxng-handler
                   (searxng-handler {:registry registry :plugin :pinboard}
                                    {:query (:query args)}))]
    {:content [{:type "text"
                :text (json/generate-string {:bookmarks bookmarks
                                             :enrichment enriched})}]}))

;; Tool declares its dependency
{:name :pinboard/search-bookmarks
 :description "..."
 :inputSchema {...}
 :dependencies [:searxng/search]  ;; registry validates this exists
 :handler (fn [context args] (search-bookmarks (:registry context) config args))}
```

**Dependency validation at registration time**:
```clojure
;; Inside register!'s swap!
(let [declared-deps (->> (:tools plugin)
                         (mapcat :dependencies)
                         (keep identity)
                         set)
      missing (clojure.set/difference declared-deps (set (keys tool-index)))]
  (when (seq missing)
    (throw (ex-info (str "Missing tool dependencies: " (str/join ", " missing))
                    {:missing missing :plugin pname}))))
```

### Token Bloat Strategy

Exposing 50+ tools from 5 plugins wastes LLM context window. Solutions:

1. **Lazy tool loading** — only include tools in `tools/list` that match a filter
2. **Tool groups** — plugins declare tool categories, client requests specific groups
3. **Dynamic tool hints** — server sends tool hints based on conversation context

```clojure
;; Tool groups
{:name :pinboard
 :tools [
   {:name :pinboard/list-bookmarks
    :group :read-only}
   {:name :pinboard/add-bookmark
    :group :write}
   {:name :pinboard/delete-bookmark
    :group :write}]}

;; Filtered tools/list
(defn tools-for-group [registry groups]
  (->> @registry :tool-index
       (filter (fn [[name meta]]
                 (let [tool (find-tool-by-name registry name)]
                   (contains? (set (:group tool)) groups))))
       (map (fn [[name meta]] ...))
       vec))
```

**Phase 1**: No bloat mitigation — our plugin count is small (5-10 tools total).
**Phase 2**: Add `:group` metadata to tools, support filtered `tools/list`.
**Phase 3**: Dynamic tool hints based on conversation context (requires sampling capability).

### Testing Strategy

Given the Hillel Wayne principles, we use **property-based testing** (test.check) for invariants:

```clojure
;; Property: registering a plugin never creates duplicate tool names
(defspec no-duplicate-tools-after-register 100
  (prop/for-all [plugins (gen/vector plugin-gen 1 5)]
    (try
      (let [registry (reg/create)]
        (reduce reg/register! registry plugins)
        true)  ;; no exception = pass
      (catch Exception e
        ;; Exception is only allowed for plugin-name collisions, not tool-name
        (not= :tool-collision (:type (ex-data e)))))))

;; Property: index always matches tools count
(defspec index-consistent-with-tools 100
  (prop/for-all [plugins (gen/vector (plugin-gen {:unique-names true}) 1 5)]
    (let [registry (reduce reg/register! (reg/create) plugins)]
      (= (count (keys @(:tool-index @registry)))
         (count (reg/all-tools registry))))))

;; Property: find-tool-handler returns handler for every registered tool
(defspec every-tool-has-handler 100
  (prop/for-all [plugins (gen/vector (plugin-gen {:unique-names true}) 1 5)]
    (let [registry (reduce reg/register! (reg/create) plugins)]
      (every? (fn [tool]
                (some? (reg/find-tool-handler registry (:name tool))))
              (reg/all-tools registry)))))
```

**Test coverage targets**:
- `registry.cljc`: 100% — all invariants covered by property tests
- `transport/streamable_http.clj`: 80%+ — HTTP request/response cycles
- `impl/promise.cljc`: 90%+ — all promise operations across runtimes
- Integration: unified server with 3+ plugins, end-to-end tool calls

### Deployment Story

**Standalone servers** (one plugin per process):
```dockerfile
FROM babashka/babashka:latest
COPY pinboard_mcp.bb /app/
EXPOSE 3000
CMD ["bb", "/app/pinboard_mcp.bb"]
```

**Unified server** (multi-plugin, JVM):
```dockerfile
FROM eclipse-temurin:21-jre
COPY unified-server.jar /app/
EXPOSE 3000 3001
CMD ["java", "-jar", "/app/unified-server.jar"]
```

**Resource profile** (estimated):
- Standalone Babashka server: ~50MB RSS, <10ms cold start
- Unified JVM server (5 plugins): ~200MB RSS, ~2s cold start
- Per-request overhead: <1ms for registry lookup, dominated by tool execution

**Scaling**:
- Single instance handles ~1000 req/s for typical tool workloads
- Multi-instance: each instance has its own registry (no shared state needed)
- Load balancer distributes across instances (sticky sessions for SSE)

### In-Process Isolation (Mode 3)

In-process plugin loading has **no sandboxing**. A buggy or malicious plugin can:
- Read other plugins' config (API tokens, credentials)
- Call other plugins' handlers directly
- Crash the entire server process

**Mitigations for trusted plugins** (our use case):
- All plugins are ours — we control the code
- Config is passed to `create-plugin`, not stored globally
- Plugin code is reviewed before registration

**Future: optional sandboxing**
- Babashka: `*sandbox*` dynamic var restricts file/network access
- JVM: SecurityManager (deprecated) or custom classloader
- Separate process: fall back to Mode 1 (remote) for untrusted plugins

---

## 12. Architecture Decisions

### Why Tool + Handler Together?

Eliminates the split-model problem (Eric Normand). One value = one source of truth. No string-based name matching. No impossible states.

### Why Registry as Atom with Derived Index?

Simple, sufficient for single-instance servers. The derived index gives O(1) lookup while maintaining a single source of truth (the plugins map). The index is rebuilt inside `swap!` so it's always consistent. If multi-instance is needed later, swap the atom for a Redis-backed store — the API (`register!`, `all-tools`, `find-tool-handler`) stays the same.

### Why Namespaced Keywords Internally?

- Clojure namespace semantics for free (`:art19/list-episodes` vs `"art19__list_episodes"`)
- No separator collisions (what if a tool name contains `__`?)
- LLMs see the string form at the protocol boundary, which is fine
- `munge-name`/`unmunge-name` is a single-point conversion

### Why Not Remove Promesa?

Promesa remains a hard dependency for JVM/CLJS. The shim simply doesn't call it under Squint. This is zero breaking changes.

### Why http-kit for Streamable HTTP?

- Works in both JVM and Babashka
- Supports SSE via `as-channel`
- Already a dependency of mcp-toolkit (for SSE)
- Session management via atoms is simple and sufficient

### Admin vs Standard Tools

Governance/PII/audit tools are split into two plugins:
- `:injector` (standard) — `scan-pii`, `get-stats`, `route-prompt` — available to all sessions
- `:injector-admin` (privileged) — `restore-pii`, `query-audit`, `check-tool-access` — separate endpoint, admin auth required

This eliminates the privilege escalation vector of exposing sensitive tools to all connected agents.

### Fork vs PR Strategy

1. PR to metosin first (shim is additive, non-breaking)
2. If not merged in 2 weeks, fork
3. Fork tracks upstream main, rebases our changes on top
4. Registry and transport are additive — unlikely to conflict

---

## 13. The Path Forward

### Immediate (This Session)

1. **Create `mcp-toolkit.registry`** — plugin registry with validation, O(1) index, collision detection in `swap!`
2. **Add `munge-name`/`unmunge-name`** — namespaced keyword ↔ protocol string conversion
3. **Update `create-session`** — accept `:registry` option, merge tools from registry
4. **Update `tool-call-handler`** — route through registry index, add timeout support

### Short-term (1-2 Weeks)

5. **Add Streamable HTTP transport** — extract from art19-mcp pattern
6. **Verify Babashka support** — run tests under bb
7. **Add internal promise shim** — `mcp-toolkit.impl.promise`
8. **Add property-based tests** — test.check for registry invariants
9. **Migrate pinboard-mcp** — smallest server, proof of concept
10. **Add `example/server-http/`** — working reference

### Medium-term (1 Month)

11. **Migrate art19-mcp** — plugin + standalone
12. **Migrate podhome-mcp** — plugin + standalone
13. **Migrate searxng-mcp** — plugin + standalone
14. **Add mcp-injector plugin mode** — in-process plugin loading
15. **Create mcp-injector MCP server** — split admin/standard plugins
16. **Add observability** — structured logging, metrics hooks

### Long-term (Ongoing)

17. **Squint example** — `example/server-squint/`
18. **Unified server** — load all plugins, one endpoint
19. **Help Metosin maintain** — upstream PRs for registry + shim
20. **Document the stack** — AGENTS.md, READMEs, migration guides
21. **Token bloat mitigation** — tool groups, filtered `tools/list`
22. **Cross-plugin tool calling** — dependency validation, context passing

---

## References

### MCP Ecosystem

| Project | Language | Approach | Stars | Notes |
|---------|----------|----------|-------|-------|
| **1MCP** | TypeScript | Unified server, multiple backends | 406 | Proxy pattern — exactly our Mode 2 |
| **AgentGateway** | Go | MCP multiplexing/federation | — | Proxy with tool filtering |
| **Armory** | Python | MCP gateway (OpenRouter for MCP) | — | Aggregates tools from multiple servers |
| **Nexus-MCP** | TypeScript | Unified gateway, 4-phase workflow | — | Addresses "Tool Space Interference" |
| **mcp-kit** | Rust | MCP server framework with plugins | — | Only Rust MCP lib with explicit plugin system |
| **Latacora mcp-sdk** | Clojure | Ring + Malli, `state/add-tool` | — | Similar tool+handler, no registry |
| **Gaiwan mcp-sdk** | Clojure | Pure Clojure, `state/add-tool` | — | Similar tool+handler, no registry |

### Security

- [CVE-2026-30856](https://vulnerablemcp.info) — Tool name collision attack against WeKnora framework
- [SlowMist MCP Security Checklist](https://github.com/slowmist) — Multi-MCP scenario security
- [modelcontextprotocol-security.io](https://modelcontextprotocol-security.io) — Tool collision rated High severity

### Design Principles

- [Simple Made Easy](https://www.infoq.com/presentations/Simple-Made-Easy/) — Rich Hickey
- [Grokking Simplicity](https://ericnormand.me/gs) — Eric Normand
- [Practical TLA+](https://www.hillelwayne.com/post/practical-tla/) — Hillel Wayne
- [Malli, Data Modelling for Clojure Developers](https://www.metosin.fi/blog/2024-01-16-malli-data-modelling-for-clojure-developers) — Tommi Reiman

### Spec & Libraries

- [MCP Transport Spec (2025-11-25)](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)
- [metosin/mcp-toolkit](https://github.com/metosin/mcp-toolkit)
- [metosin/malli](https://github.com/metosin/malli)
- [vamp](~/src/vamp/) — Async lib for Squint/CLJS
- [mcp-toolkit-squint-compat-spec.md](~/src/vamp/mcp-toolkit-squint-compat-spec.md)

### JB Ecosystem

- [art19-mcp](~/src/art19-mcp/art19_mcp.bb) — Reference Streamable HTTP impl
- [podhome-mcp](~/src/podhome-mcp/podhome_mcp.clj) — Streamable HTTP impl
- [pinboard-mcp](~/src/pinboard-mcp/pinboard_mcp.bb) — Smallest server
- [searxng-mcp](~/src/searxng-mcp/) — Search metasearch
- [mcp-injector](~/src/mcp-injector/) — MCP gateway (consumer + future server)

---

## Appendix: Invariants Checklist

These should be structurally guaranteed by the data model:

- [ ] Every registered plugin passes `plugin-schema` validation
- [ ] Every tool in a plugin passes `tool-schema` validation
- [ ] No duplicate plugin names in a registry
- [ ] No duplicate tool names across plugins in a registry (checked inside `swap!`)
- [ ] Every tool has a non-nil `:handler` function
- [ ] Every `:inputSchema` is valid JSON Schema
- [ ] Index count equals total tool count (derived data consistency)
- [ ] `find-tool-handler` returns non-nil for every registered tool
- [ ] `tools/list` response matches `registry/all-tools` (after munging)
- [ ] `tools/call` routes to the correct handler
- [ ] Every handler returns `{content [...]}` or `{content [...] :isError true}`
- [ ] Tool dependencies exist at registration time
- [ ] Config schemas are valid Malli schemas

---

## Appendix: art19-mcp Key Patterns

### Session Management (lines 47-67)

```clojure
(def sessions (atom {}))
(defn new-session-id [] (str (java.util.UUID/randomUUID)))
(defn create-session! [] ...)
(defn valid-session? [sid] ...)
```

### HTTP Handler (lines 1046-1082)

```clojure
(case (:request-method request)
  :post (handle-mcp request config)
  {:status 405 :body "Method Not Allowed"})
```

### JSON-RPC Dispatch (lines 1025-1037)

```clojure
(case method
  :initialize (handle-initialize id params)
  :notifications/initialized nil
  :tools/list (handle-tools-list id params)
  :tools/call (handle-tools-call id params config))
```

---

_Plan updated: 2026-04-03_
_Related: vamp, art19-mcp, podhome-mcp, pinboard-mcp, searxng-mcp, mcp-injector, mcp-toolkit-squint-compat-spec.md_
