# mcp-toolkit — Agent Dev Contract

## Dev Discipline

### Before You Start Working

1. **Read PLAN.md** — understand the current phase and what you're building
2. **Check `git status`** — know the current branch state
3. **Check nREPL** — ensure it's running (`clojure-dev_list_nrepl_ports`)
4. **Run clj-kondo** — `clj-kondo --lint src` — know the baseline

### While Working

1. **REPL-first** — evaluate every function before moving on
   - Start nREPL: `clj -M:nrepl` (port 7888)
   - Use `clojure-dev_clojure_eval` to test incrementally
   - Never write large blocks without REPL verification

2. **Format often** — `cljfmt fix src/` after each meaningful edit
3. **Lint often** — `clj-kondo --lint src/` after each meaningful edit
4. **Commit often** — each commit is a snapshot you can roll back to
   - Commit message format: `phase-N: short description of what changed`
   - Include WHY in the commit message, not just WHAT

3. **Update breadcrumbs** — after each commit, update the Dev Log below

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

**Current branch**: `feat/streamablehttp`
**Current phase**: Phase 2 done (Streamable HTTP). Ready for Phase 3: pinboard-mcp migration as proof of concept
**nREPL**: port 7890 (started via `clj -M:nrepl --port 7890`)

**Key decisions made this session**:
- Plugin registry with O(1) derived index, collision detection in `swap!`
- Namespaced keywords internally (`:art19/list-episodes`), munged to strings at protocol boundary
- Admin vs standard plugin split for mcp-injector (security)
- Fork-vs-PR strategy for metosin upstream
- Deferred: Squint, cross-plugin calls, token bloat, observability, hot-reload

**Next**: Phase 3 — migrate pinboard-mcp to plugin format, then unified server example

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
├── impl/
│   ├── common.cljc        ;; Shared utilities (user-callback)
│   ├── server/
│   │   └── handler.cljc   ;; JSON-RPC method handlers
│   └── client/
│       └── handler.cljc   ;; Client-side handlers
├── registry.cljc          ;; NEW: Plugin registry (Sprint 1)
└── transport/
    └── streamable_http.clj ;; NEW: Streamable HTTP transport (Sprint 1-2)
```

## Current Dependencies

- `taipei.404/mate` — utility functions
- `funcool/promesa` — async/promises (JVM/CLJS)
- `org.clojure/clojure` 1.12.1
- `org.clojure/clojurescript` 1.12.42

## Target Dependencies (Sprint 1-2)

- `metosin/malli` — schema validation (for registry)
- `http-kit/http-kit` — HTTP server (for transport)
- `cheshire/cheshire` — JSON (for transport)
