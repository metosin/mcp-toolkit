# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

Versions prior to v0.1.0 are considered experimental, their API may change.

## [Unreleased]

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
