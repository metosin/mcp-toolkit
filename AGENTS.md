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

### After Completing a Task

1. **Run full lint**: `clj-kondo --lint src/`
2. **Run full format**: `cljfmt fix src/`
3. **Commit the snapshot**
4. **Update PLAN.md** if the plan changed
5. **Update this file's Dev Log**

---

## Dev Log

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

**Current branch**: `feat/streamablehttp`
**Current phase**: Phase 2 streamable_http written (ORIGINAL version, NOT patched). Needs spec-compliance fixes — NOT YET APPLIED.
**nREPL**: port 7890 (started via `clj -M:nrepl --port 7890`)
**Test status**: 28 tests, 65 assertions, 0 failures (kaocha JVM + CLJS)
**Lint**: src/ = 0 errors, 0 warnings. test/ = 1 error, 9 warnings (pre-existing from upstream)

**Key decisions made this session**:
- Plugin registry with O(1) derived index, collision detection in `swap!`
- Namespaced keywords internally (`:art19/list-episodes`), munged to strings at protocol boundary
- Admin vs standard plugin split for mcp-injector (security)
- Fork-vs-PR strategy for metosin upstream
- Deferred: Squint, cross-plugin calls, token bloat, observability, hot-reload

**CRITICAL: streamable_http.clj still needs these fixes (NOT applied)**:
1. Add `supported-protocol-versions` constant + `validate-protocol-version` function
2. Replace `create-session-store` to use `{:sessions {} :terminated {}}` structure
3. Replace `valid-session?` to check `:sessions` key
4. Add `terminated-session?` function
5. Replace `delete-session!` with atomic tombstone version
6. Replace session check in `handle-post` with cond: missing-id (400), terminated (404), invalid (400)
7. Add protocol-version check in `handle-post` before session check
8. Replace `handle-get` session check to also check `terminated-session?`
9. Replace `handle-delete` to handle `:existed`/`:already-terminated`/`:not-found` returns
10. Add `handle-dispatch-response` helper for request vs notification detection

The original `streamable_http.clj` on disk is the CLEAN, WORKING version. All patch attempts
failed with paren mismatches. DO NOT try to patch it. Either:
(a) copy the whole function from a scratch file that's been verified in nREPL, or
(b) rewrite the entire file in one write operation and verify balance first

**Next**: Fix streamable_http.clj spec compliance (10 changes above), then write HTTP tests

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
    └── streamable_http.clj ;; Streamable HTTP transport (Phase 2, UNPATCHED)

test/mcp_toolkit/
├── core_test.cljc           ;; Existing handshake test (CSP channels)
├── registry_test.clj        ;; 23 unit + 4 property tests (test.check)
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

---

## How to Fix streamable_http.clj (Next Session)

The working file at `src/mcp_toolkit/transport/streamable_http.clj` (285 lines) is the ORIGINAL
unpatched version. It needs the following 10 changes per the MCP 2025-11-25 spec review.

**APPROACH**: For EACH change below, write the new code to a small scratch file, load it in
nREPL to verify it parses, then use `clojure-dev_clojure_edit` to replace the old version
in the main file.

1. **Add protocol versions** — Insert after imports (line ~22):
   - `supported-protocol-versions` constant
   - `validate-protocol-version` function

2. **Replace create-session-store** (line 29): change `(atom {})` → `(atom {:sessions {} :terminated {}})`

3. **Replace valid-session?** (line 44): change `(contains? @store sid)` → `(contains? (:sessions @store) sid)`

4. **Add terminated-session?** — Insert after `valid-session?`:
   ```clojure
   (defn terminated-session? [store sid]
     (and sid (contains? (:terminated @store) sid)))
   ```

5. **Replace delete-session!** (lines 52-58) — Use `clojure-dev_clojure_edit` with form_type `defn`, form_identifier `delete-session!`, operation `replace`

6. **Update handle-post session validation** — The `if` on line 162 that checks `valid-session?` needs to become a `cond` with 3 cases: missing-id, terminated, invalid

7. **Add protocol version check to handle-post** — Check `validate-protocol-version` before session validation

8. **Update handle-get** (line 189) — Add `terminated-session?` check before `valid-session?` check

9. **Update handle-delete** (line 212) — Replace `(if (delete-session! ...) ...)` with `(case (delete-session! ...) :existed ... :already-terminated ... :not-found ...)`

10. **Add handle-dispatch-response helper** — Insert before `handle-post` definition. This handles request vs notification detection via `:id` field.
