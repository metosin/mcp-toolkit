# MCP Toolkit Upgrade Checklist: 2025-03-26 → 2025-06-18

## Phase 1: Protocol Version Updates
- [x] Add "2025-06-18" to server-supported-protocol-versions
- [x] Update default client protocol-version to "2025-06-18"
- [ ] Update deps.edn version to indicate 2025-06-18 support

## Phase 2: Capability Changes
- [ ] Review and update server capabilities in initialize-handler
- [ ] Review and update client capabilities in create-session
- [ ] Check for new capability fields or structures

## Phase 3: Message Format Updates
- [x] Review all message handlers for format changes
- [x] Update resource-related messages (list, read, subscribe)
- [x] Update tool-related messages (list, call)
- [x] Update prompt-related messages (list, get)
- [x] Update completion messages (context field support)
- [ ] Update progress notification format
- [ ] Update logging message format

## Phase 4: New Features
- [x] Remove JSON-RPC batching support (2025-06-18 breaking change)
- [x] Add title field support for prompts, resources, and tools
- [x] Add _meta field support utilities
- [x] Implement structured tool output (outputSchema support)
- [x] Add resource links in tool results
- [x] Add context field to CompletionRequest
- [ ] Add elicitation support (optional, lower priority)

## Phase 5: Error Handling
- [ ] Review error codes for changes
- [ ] Update error response formats if needed
- [ ] Add new error types if introduced

## Phase 6: Testing
- [x] Update handshake tests for new version
- [x] Test backward compatibility with 2025-03-26
- [x] Test forward compatibility with 2025-06-18
- [x] Test JSON-RPC batching removal
- [x] Test title field support
- [x] Test structured tool output
- [x] Test resource links in tool results
- [x] Test completion context field
- [x] Test _meta field utilities
- [ ] Update example projects to demonstrate new features
- [ ] Test with MCP Inspector and Claude Desktop

## Phase 7: Documentation
- [ ] Update README.md compatibility notes
- [ ] Update CHANGELOG.md with 2025-06-18 support
- [ ] Update example configurations
- [ ] Update API documentation

## Phase 8: Breaking Changes
- [x] Identify any breaking changes in 2025-06-18
- [x] Update migration guide
- [ ] Consider deprecation warnings for removed features
