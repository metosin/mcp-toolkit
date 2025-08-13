# MCP Toolkit Upgrade Plan: 2025-03-26 → 2025-06-18

## Overview
This document outlines the upgrade plan for MCP Toolkit to support the 2025-06-18 specification, based on the official changelog from https://modelcontextprotocol.io/specification/2025-06-18/changelog

## Specification Changes Summary

### Major Changes
1. **Remove JSON-RPC batching support** (Breaking change)
2. **Structured tool output** - Tools can define output schemas
3. **OAuth Resource Server classification** - Security enhancement for HTTP transport
4. **RFC 8707 Resource Indicators** - Required for OAuth clients
5. **Elicitation support** - Servers can request additional info from users
6. **Resource links in tool results** - Tools can reference resources
7. **MCP-Protocol-Version header** - Required for HTTP transport

### Schema Changes
1. **_meta field additions** - Added to more interface types
2. **context field in CompletionRequest** - Include previously-resolved variables
3. **title field** - Human-friendly display names (name becomes programmatic ID)

## Implementation Phases

### ✅ Phase 1: Protocol Version Updates (COMPLETED)
- [x] Add "2025-06-18" to server-supported-protocol-versions
- [x] Update default client protocol-version to "2025-06-18"
- [x] Update README.md compatibility notes
- [x] Add tests for protocol negotiation and backward compatibility

### Phase 2: Breaking Changes
- [ ] Check if JSON-RPC batching is implemented
- [ ] Remove JSON-RPC batching support if present
- [ ] Update tests to ensure batching is rejected

### Phase 3: Basic Schema Updates
- [ ] Add `title` field to prompts, tools, and resources
  - [ ] Update server data structures
  - [ ] Update client data structures
  - [ ] Update handler code
  - [ ] Add tests for title field
- [ ] Add `_meta` field support to messages
  - [ ] Identify all message types that need _meta
  - [ ] Add optional _meta field
  - [ ] Document proper usage

### Phase 4: Enhanced Features
- [ ] Implement structured tool output
  - [ ] Add outputSchema field to tool definitions
  - [ ] Update tool result handling
  - [ ] Add validation for structured output
  - [ ] Create example with structured output
- [ ] Add resource links in tool results
  - [ ] Update tool result structure
  - [ ] Handle resource links in responses
  - [ ] Add tests for resource links
- [ ] Add context field to CompletionRequest
  - [ ] Update completion request structure
  - [ ] Handle context in completion handler
  - [ ] Test context passing

### Phase 5: Advanced Features (Lower Priority)
- [ ] Elicitation support (optional client capability)
  - [ ] Add elicitation to client capabilities
  - [ ] Implement elicit method handler
  - [ ] Add server-side elicitation support
  - [ ] Create elicitation example

### Phase 6: HTTP Transport Updates (If Applicable)
- [ ] Add MCP-Protocol-Version header to HTTP requests
- [ ] Implement OAuth Resource Server metadata
- [ ] Add RFC 8707 Resource Indicators support

### Phase 7: Testing & Validation
- [ ] Update all existing tests for 2025-06-18
- [ ] Add tests for new features
- [ ] Test with MCP Inspector
- [ ] Test with Claude Desktop
- [ ] Validate example projects

### Phase 8: Documentation
- [ ] Update CHANGELOG.md
- [ ] Update API documentation
- [ ] Update example configurations
- [ ] Create migration guide for users

## Implementation Priority

### Immediate (Core Compatibility)
1. ✅ Protocol version negotiation
2. Remove JSON-RPC batching
3. Add title field support
4. Add _meta field support

### Next (Enhanced Features)
5. Structured tool output
6. Resource links in tool results
7. Context in CompletionRequest

### Future (Advanced/Optional)
8. Elicitation support
9. HTTP transport updates
10. OAuth enhancements

## Current Status

- **Branch**: support-2025-06-18-spec
- **Last Commit**: Added protocol version support and tests
- **Tests**: All passing
- **Next Step**: Check and remove JSON-RPC batching support

## Testing Strategy

For each change:
1. Write tests first (TDD approach)
2. Implement the change
3. Verify tests pass
4. Test with example projects
5. Document the change

## Notes

- The toolkit primarily uses STDIO transport, so HTTP-specific changes are lower priority
- Maintain backward compatibility with 2025-03-26 where possible
- Focus on changes that affect the core client-server communication first
