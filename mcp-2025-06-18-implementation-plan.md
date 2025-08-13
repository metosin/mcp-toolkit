# MCP 2025-06-18 Specification Changes Summary

## Overview
This document summarizes the actual changes from the MCP specification 2025-03-26 to 2025-06-18 based on the official changelog.

## 1. Breaking Changes

### 1.1 Remove JSON-RPC Batching Support
- **Change**: JSON-RPC batching is no longer supported
- **Impact**: Need to verify if our JSON-RPC implementation handles batch requests and remove support if present
- **File**: `src/mcp_toolkit/json_rpc.cljc`

## 2. New Features

### 2.1 Structured Tool Output
- **Change**: Tools can now define structured output schemas
- **Impact**: Add support for tools to return structured content with explicit schemas
- **Implementation**: 
  - Add `outputSchema` field to tool definitions
  - Update tool result handling to support structured content

### 2.2 Elicitation Support
- **Change**: Servers can request additional information from users during interactions
- **Impact**: This is a new client capability that servers can use
- **Implementation**:
  - Add `elicitation` capability to client capabilities
  - Implement `elicit` method handler in client
  - Add support for elicitation requests in server

### 2.3 Resource Links in Tool Results
- **Change**: Tool call results can now include resource links
- **Impact**: Tool results can reference resources
- **Implementation**:
  - Update tool result structure to support `resources` field
  - Handle resource links in tool responses

## 3. Security Updates

### 3.1 OAuth Resource Server Classification
- **Change**: MCP servers are now classified as OAuth Resource Servers
- **Impact**: Servers should include protected resource metadata for authorization server discovery
- **Note**: This primarily affects HTTP/SSE transport, not STDIO

### 3.2 RFC 8707 Resource Indicators
- **Change**: MCP clients must implement Resource Indicators (RFC 8707)
- **Impact**: Clients must specify the intended resource server in token requests
- **Note**: This primarily affects HTTP/SSE transport with OAuth

## 4. Protocol Updates

### 4.1 MCP-Protocol-Version Header
- **Change**: HTTP transport must include `MCP-Protocol-Version` header in subsequent requests
- **Impact**: When using HTTP transport, add this header after initialization
- **Note**: Only affects HTTP transport implementations

### 4.2 _meta Field Additions
- **Change**: Add `_meta` field to additional interface types
- **Impact**: Various message types can now include `_meta` for metadata
- **Implementation**:
  - Add optional `_meta` field to relevant message types
  - Document proper usage of `_meta` field

### 4.3 Context Field in CompletionRequest
- **Change**: Add `context` field to `CompletionRequest`
- **Impact**: Completion requests can now include previously-resolved variables
- **Implementation**:
  - Add `context` field to completion request structure
  - Handle context in completion handler

### 4.4 Title Field for Display Names
- **Change**: Add `title` field for human-friendly display names
- **Impact**: `name` becomes programmatic identifier, `title` for display
- **Implementation**:
  - Add optional `title` field to prompts, tools, and resources
  - Update documentation to clarify name vs title usage

## Implementation Priority

Given that this toolkit focuses on STDIO transport and basic functionality:

### High Priority (Core functionality)
1. ✅ Protocol version support (DONE)
2. Remove JSON-RPC batching support (if present)
3. Add `title` field support to prompts, tools, resources
4. Add `_meta` field support to messages

### Medium Priority (Enhanced features)
5. Structured tool output support
6. Resource links in tool results
7. Context field in CompletionRequest

### Low Priority (Advanced features, mainly for HTTP transport)
8. Elicitation support (client capability)
9. MCP-Protocol-Version header (HTTP only)
10. OAuth/Security updates (HTTP/SSE only)

## Next Steps

1. Check and remove JSON-RPC batching support
2. Add `title` field to resource/tool/prompt definitions
3. Add `_meta` field support
4. Implement structured tool output
5. Test all changes with example projects
