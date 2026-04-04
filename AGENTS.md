# mcp-toolkit — Agent Dev Contract

## Dev Discipline

### Before You Start Working

1. **Read PLAN.md** — understand the current phase and what you're building
2. **Check `git status`** — know the current branch state
3. **Check nREPL** — ensure it's running (`clojure-dev_list_nrepl_ports`)
4. **Run clj-kondo** — `clj-kondo --lint src` — know the baseline

### While Working

1. **REPL-first** — evaluate every function before moving on
   - Start nREPL: `clj -M:nrepl --port 7890`
   - Use `clojure-dev_clojure_eval` with `:port 7890`
   - Never write large blocks without REPL verification

2. **Format often** — `cljfmt fix src/` after each meaningful edit
3. **Lint often** — `clj-kondo --lint src/` after each meaningful edit
4. **Commit often** — each commit is a snapshot you can roll back to
   - Commit message format: `phase-N: short description of what changed`
   - Include WHY in the commit message, not just WHAT
5. **Update breadcrumbs** — after each commit, update the Dev Log below

### Critical Lessons from Session 2026-04-03

1. **Writing files is HARD** — Python heredocs, sed, and bash all corrupt Clojure parens. Use `write` tool for new files only. For edits, use `clojure-dev_clojure_edit`. If that fails, write a scratch file, verify it independently, then copy-paste the working version.

2. **Paren hell is real** — Don't hand-count closing parens in deeply nested functions. Strategy: write the function in isolation to a scratch file, load it in nREPL to verify it parses, then swap it in with a single atomic replacement of the whole function.

3. **Targeted patches beat rewrites** — When fixing a function, change ONLY the part that needs changing. Keep the original structure and closing parens intact. Replace small blocks, not entire functions.

4. **nREPL port conflicts** — clojure-mcp runs its own nREPL on 7888. Always use port 7890 for mcp-toolkit. If nREPL has stale classpath, kill it and restart: `fuser -k 7890/tcp && cd /home/wes/src/mcp-toolkit && clj -M:nrepl --port 7890 &`

5. **Fixture arity bugs** — `handler-response` takes `[handler method uri & [{:keys [headers body]}]]`. If you pass headers and body as separate positional args, the body gets silently ignored. Always pass `{:headers ... :body ...}` as the single optional map.

6. **Case-insensitive headers** — http-kit lowercases header names in real requests. `find-header` now handles keyword keys, string keys, and any case. Fixtures pass headers with their original case (`"MCP-Session-Id"`), transport handles them all.

### After Completing a Task

1. **Run full lint**: `clj-kondo --lint src/`
2. **Run full format**: `cljfmt fix src/`
3. **Commit the snapshot**
4. **Update PLAN.md** if the plan changed
5. **Update this file's Dev Log**

---

## Dev Log

### Session: 2026-04-04 — Phase 3 Discovery + Promise Shim Design

| Commit | What | Why |
|--------|------|-----|
| _(pending)_ | PLAN.md — Phase 3 rewrite, ecosystem inventory | Replaced incorrect "promesa works in bb" with CompletableFuture shim design |
| _(pending)_ | PLAN.md — updated architecture decisions, path forward | Added "Why shim vs reader conditionals", phase status table |

**Key research findings**:
- **Promesa does NOT load in Babashka** — `extend-protocol clojure.core/Inst` fails in SCI (no `Inst` protocol)
- **CompletableFuture works natively in bb** — verified: `.complete`, `.thenApply`, `.handle`, `.orTimeout`, `.allOf` all work
- **Only 6 promesa functions used, 15 call sites** — `p/create`, `p/then`, `p/catch`, `p/handle`, `p/all`, `p/timeout`
- **Virtual threads on JVM (JDK 21)** — no need to abandon promesa on JVM; ForkJoinPool → vthreads is automatic for I/O-bound handlers
- **SCI got async/await in 2026** — CLJS-3470 implemented in SCI, but still uses JS Promises for nbb/scittle, not bb
- **`babashka/sci.configs/funcool/promesa` exists** — SCI config for promesa functions, but meant for nbb/scittle, not standalone bb

**Architecture decision**: Internal promise shim `mcp-toolkit.impl.promise`:
- `:clj` → promesa 11 (JVM, virtual threads via ForkJoinPool)
- `:bb` → `java.util.concurrent.CompletableFuture` (native in bb, zero deps)
- `:cljs` → promesa (ClojureScript, goog.Promise)
- `:squint` → `js/Promise` (future, trivial addition once shim exists)

**Ecosystem inventory discovered**:
- art19-mcp, podhome-mcp, pinboard-mcp, searxng-mcp — all Babashka, all Streamable HTTP
- hedgedoc-mcp — Squint, STDIO
- mcp-stdio-proxy — Clojure, STDIO bridge
- mcp-injector — Clojure, HTTP consumer + future server

**Decisions**:
- Tests are first-class for the shim (unit + property-based)
- Zero call-site changes — swap 4 imports, done
- Squint support deferred to later, just adding `:squint` branch to shim
- Plugin migration (Phase 5) is next after shim, starting with pinboard-mcp

**Next**: Build `impl/promise.cljc`, write tests, swap imports, verify everything passes.

---

### Session: 2026-04-03 — Plan Review & Architecture

| Commit | What | Why |
|--------|------|-----|
| ae9f568 | PLAN.md — comprehensive unified plan | Baseline architecture with plugin registry, transport, mcp-injector integration |
| fba5c35 | AGENTS.md — dev contract | Establishes dev discipline: REPL-first, cljfmt/kondo, commit snapshots |
| 59e2adb | `registry.cljc` — plugin registry | Sprint 1.1-1.2: schemas, register!, O(1) index, collision detection in swap!, Malli validation |
| 89b63d0 | lint: fix all 9 pre-existing kondo warnings | Clean baseline: 0 errors, 0 warnings |
| 0c54c24 | munge-name/unmunge-name in impl/common.cljc | Protocol boundary conversion, round-trip verified |
| 5613f25 | wire registry into server.cljc + handler.cljc | create-session accepts :registry, tool-call routes through O(1) index, REPL-verified two-plugin dispatch |
| f55c64c | phase-2: Streamable HTTP transport | 2025-11-25 spec: POST/GET/DELETE, Origin validation, SSE, session pruning, 0 lint warnings |
| d68a17a | test infrastructure: test.check, fixtures | kaocha + test.check running, 23 unit tests + 4 property-based (defspec), mock HTTP fixtures |
| 6720e75 | fix: test helpers generate unique tool names | make-tool uses cond-> for optional fields, make-plugin unique defaults, 28 tests 0 failures |
| ded498d | phase-2a: streamable_http 10 MCP spec fixes | Protocol version validation, session tombstone, 3-way session check, handle-dispatch-response helper |
| d03a888 | phase-2b: HTTP integration tests + find-header fix | 24 new HTTP tests, fixture arity fixes, case-insensitive header lookup, 52 tests 0 failures |

**Current branch**: `feat/streamablehttp`
**Current phase**: Phase 2 COMPLETE — streamable_http spec-compliant + 24 HTTP tests passing.
**nREPL**: port 7890 (started via `clj -M:test:nrepl --port 7890`)
**Test status**: 52 tests (28 unit + 24 HTTP), 101 assertions, 0 failures (kaocha JVM + CLJS)
**Lint**: src/ = 0 errors, 0 warnings. test/ = 1 error, 9 warnings (pre-existing from upstream)

**Key decisions made this session**:
- Plugin registry with O(1) derived index, collision detection in `swap!`
- Namespaced keywords internally (`:art19/list-episodes`), munged to strings at protocol boundary
- Admin vs standard plugin split for mcp-injector (security)
- Fork-vs-PR strategy for metosin upstream
- Deferred: Squint, cross-plugin calls, token bloat, observability, hot-reload
- find-header now handles keyword AND string keys with case-insensitive matching (critical for test mocks vs real http-kit)
- Fixture `post-json`/`get-request`/`delete-request` use `{:headers ... :body ...}` map consistently

**Phase 2A applied** — streamable_http.clj (354 lines) is now spec-compliant with MCP 2025-11-25:
- supported-protocol-versions constant + validate-protocol-version using find-header
- Session store uses `{:sessions {} :terminated {}}` with tombstone lifecycle
- deleted-session! returns `:existed`/`:already-terminated`/`:not-found`
- handle-post: 3-way cond (missing-id→400, terminated→404, invalid→400)
- handle-dispatch-response helper for request vs notification detection

**Next**: Phase 2C (registry transport integration tests), then Phase 3 (Babashka support)

---

## Tool Usage Rules

### clojure-dev_clojure_edit (structural editing)
- PREFER this over generic file editing for Clojure files
- Use `form_type` + `form_identifier` to target definitions
- Operations: `replace`, `insert_before`, `insert_after`

### clojure-dev_clojure_eval (REPL)
- Use for verification, not for production code changes
- Test each function in isolation before committing
- Use `clj-mcp.repl-tools/list-ns`, `list-vars`, `doc-symbol`, `source-symbol`
- Always pass `:port 7890` explicitly

### cljfmt
- Run `cljfmt fix src/` after each edit batch
- Don't commit unformatted code

### clj-kondo
- Run `clj-kondo --lint src/` after each edit batch
- Fix warnings before committing
- Don't commit with new warnings (unless intentional, noted in commit)

### Git
- Commit after each logical unit of work
- Use `feat/streamablehttp` branch
- Never force push
- Never commit secrets or credentials

---

## Project Structure

```
src/mcp_toolkit/
├── json_rpc.cljc          ;; JSON-RPC protocol (promesa-based)
├── server.cljc            ;; Server session management
├── client.cljc            ;; Client session management
├── registry.cljc          ;; Plugin registry (Sprint 1)
├── impl/
│   ├── common.cljc        ;; Shared utilities (munge-name, user-callback)
│   ├── server/
│   │   └── handler.cljc   ;; JSON-RPC method handlers
│   └── client/
│       └── handler.cljc   ;; Client-side handlers
└── transport/
    └── streamable_http.clj ;; Streamable HTTP transport (Phase 2, SPEC-COMPLIANT)

test/mcp_toolkit/
├── core_test.cljc           ;; Existing handshake test (CSP channels)
├── registry_test.clj        ;; 23 unit + 4 property tests (test.check)
├── streamable_http_test.clj ;; 24 HTTP integration tests (Phase 2B)
└── test/
    ├── fixtures.clj         ;; with-registry, with-http-server, mock-request
    └── util.cljc            ;; Existing test utilities
```

## Current Dependencies

- `taipei.404/mate` — utility functions
- `funcool/promesa` — async/promises (JVM/CLJS)
- `metosin/malli` — schema validation (for registry)
- `org.clojure/clojure` 1.12.1
- `org.clojure/clojurescript` 1.12.42
- `org.clojure/test.check` 1.1.1 (tests)

## Test Dependencies

- `lambdaisland/kaocha` 1.91.1392 — test runner
- `lambdaisland/kaocha-cljs` 1.5.154 — CLJS tests
- `lambdaisland/chuck` 0.2.136 — test.check integration
- `http-kit/http-kit` 2.8.0 — HTTP server (tests)
- `cheshire/cheshire` 5.13.0 — JSON (tests)
