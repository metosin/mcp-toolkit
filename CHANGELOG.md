# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

Versions prior to v0.1.0 are considered experimental, their API may change.

## [Unreleased]

### Added
- **MCP Protocol 2025-06-18 Support** - Full implementation of the latest specification
  - Title fields for prompts, resources, and tools (human-readable display names)
  - Structured tool output with `outputSchema` support
  - Resource links in tool results
  - Context field in `CompletionRequest` for passing previous values
  - `_meta` field support utilities (`mcp-toolkit.impl.meta-support` namespace)
  - Automatic protocol version negotiation between client and server
  - Backward compatibility with 2025-03-26 protocol version

### Changed
- `tool-list-handler` now includes `outputSchema` when defined on tools
- `tool-call-handler` supports both simple string returns and structured responses
- `completion-complete-handler` accepts and passes context to completion functions
- Handlers preserve `title` fields alongside `name` fields

### Removed
- **Breaking:** JSON-RPC batching support removed (per 2025-06-18 spec)
  - Array requests now return an "Invalid Request" error

### Documentation
- Added comprehensive migration guide (MIGRATION-2025-06-18.md)
- Updated README with protocol version compatibility information
- Added examples for new 2025-06-18 features

## [v0.1.1-alpha] - 2025-07-03

### Fixed

- Server API: completion functions for prompts are now optional.
- Server API: completion functions for resource URIs are now optional.
- Server API: `:resource-templates` is now optional in the session.
- Server API: A promise bug when a response from an MCP client contained an error.
- Server API: The default log level, if unspecified, is now "debug".

### Changed

- `json-rpc/handle-message`'s method signature was changed from `[context]` to `[context message]`.

## [v0.1.0-alpha] - 2025-07-01

### Added

- First release 🎉
