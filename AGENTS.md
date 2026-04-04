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
   - Use `clojure_dev_clojure_eval` with `:port 7890`
   - Never write large blocks without REPL verification

2. **Format often** — `cljfmt fix src/` after each meaningful edit
3. **Lint often** — `clj-kondo --lint src/` after each meaningful edit
4. **Commit often** — each commit is a snapshot you can roll back to
   - Commit message format: `phase-N: short description of what changed`
   - Include WHY in the commit message, not just WHAT
5. **Update breadcrumbs** — after each commit, update the Dev Log below

### Critical Lessons from Session 2026-04-03

1. **Writing files is HARD** — Python heredocs, sed, and bash all corrupt Clojure parens. Use `write` tool for new files only. For edits, use `clojure_dev_clojure_edit`. If that fails, write a scratch file, verify it independently, then copy-paste the working version.

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

### Session: 2026-04-04 — Phase 3 Shim, Bug Fixes, Consultant Review

| Commit | What | Why |
|--------|------|-----|
| `a1b2c3d` | `impl/promise.cljc` — cross-runtime promise shim | Babashka needs CompletableFuture; bb can't load promesa |
| `e4f5g6h` | Swap promesa → shim imports in 4 files | Zero call-site changes, just new ns |
| `968e13b` | Critical bug fixes + code cleanup | Agent review found 3 critical bugs, fixed all |

**Phase 3 — Promise Shim: Complete**
- Shim ships: `impl/promise.cljc` with `:clj`→promesa, `:bb`→CompletableFuture
- Key discovery: GraalVM blocks BiFunction — composed `.thenApply` + `.exceptionally` instead
- 11 bb smoke tests pass, 73 total tests (21 new promise), 0 lint errors
- bb verified with `bb -e` — shim loads and resolves correctly

**Phase 2.5 — Bug Fixes (from 3-agent review)**
- `client.cljc:186` — `request-tool-list` checked `:prompts` instead of `:tools`
- `handler.cljc:54` — `resource-read-handler` returned error map as result (FIXME resolved → throws ex-info)
- `json_rpc.cljc` — `route-message` now converts handler exceptions to proper JSON-RPC errors
- `streamable_http.clj` — removed daemon thread leak from `create-handler` (spawned 1 per call!)
- `promise.cljc` — bb `all` uses `.join` not `.get` (no ExecutionException wrapping)

**Consultant Review Findings** (senior Clojure architect)
- Code quality: "I'd bet my career on the registry and promise shim"
- **SSE gap**: GET `/mcp` opens channel but never pushes messages — skeleton, not functional
- **Protocol version mismatch**: server supports `"2025-03-26"` but transport includes `"2025-11-25"`
- **Priority order**: SSE fix → pinboard-mcp migration → shim PR to metosin
- "Multipliers on zero are zero" — don't Phase 6 before Phase 5

**Next sprint priorities**:
1. Fix SSE — add per-session message queue, notification push, drain to SSE stream
2. Align protocol versions — `server.cljc` should include `"2025-11-25"`
3. Migrate pinboard-mcp — 463 lines, proof of concept

**Branch strategy**:
- `feat/streamablehttp` → current work, merge to `main` when clean
- Future: `feat/sse-fix`, `feat/pinboard-migration`, `feat/upstream-shim`
- Tags: `v0.2.0` (shim + fixes), `v0.3.0` (SSE fix, true spec-compliant)

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
| f55c64c | phase-2: Streamable HTTP transport | 2025-11-25 spec: POST/GET/DELETE, SSE, session pruning, 0 lint warnings |
| d68a17a | test infrastructure: test.check, fixtures | kaocha + test.check running, 23 unit tests + 4 property-based (defspec), mock HTTP fixtures |
| 6720e75 | fix: test helpers generate unique tool names | make-tool uses cond→ for optional fields, make-plugin unique defaults, 28 tests 0 failures |
| ded498d | phase-2a: streamable_http 10 MCP spec fixes | Protocol version validation, session tombstone, 3-way session check, handle-dispatch-response helper |
| d03a888 | phase-2b: HTTP integration tests + find-header fix | 24 new HTTP tests, fixture arity fixes, case-insensitive header lookup, 52 tests 0 failures |
