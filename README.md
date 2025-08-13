# MCP Toolkit

[![Clojars Project](https://img.shields.io/clojars/v/fi.metosin/mcp-toolkit.svg)](https://clojars.org/fi.metosin/mcp-toolkit)
[![Slack](https://img.shields.io/badge/slack-mcp--toolkit-orange.svg?logo=slack)](https://clojurians.slack.com/app_redirect?channel=mcp-toolkit)
[![cljdoc badge](https://cljdoc.org/badge/fi.metosin/mcp-toolkit)](https://cljdoc.org/d/fi.metosin/mcp-toolkit)

This library is a very unofficial MCP SDK in Clojure.

It handles the communication between MCP clients and MCP servers, and attempts to provide
a Clojure-ish experience to developers working on expending the MCP ecosystem.

Status: **alpha quality**

Tested on Claude Desktop and Claude Code, no problems found for the features implemented.

## Protocol Version Support

MCP Toolkit supports automatic protocol version negotiation between clients and servers:

- **2025-06-18** (latest) - Full support with all new features
- **2025-03-26** - Full backward compatibility
- **2024-11-05** - Legacy support

### New in 2025-06-18

- **Title fields** - Human-readable display names for better UI
- **Structured tool output** - Define output schemas for type-safe responses
- **Resource links** - Tools can return resources alongside content
- **Completion context** - Pass previous values to completion handlers
- **_meta field support** - Optional metadata for various message types
- **Breaking change:** JSON-RPC batching removed (array requests no longer supported)

📚 **[See the Migration Guide](MIGRATION-2025-06-18.md)** for upgrading existing MCP servers and clients.

## Implemented features

- [x] API for both clients & servers
- [x] CLJC
  - [x] Clojure
  - [x] Clojurescript
  - [ ] Babashka
- I/O agnostic library
- Uses Promesa to support async tasks in prompts, resources and tools
- Compatible with protocol versions
  - [x] `2024-11-05`
  - [x] `2025-03-26`
  - [x] `2025-06-18`
- MCP features implemented
  - [x] Cancellation
  - [x] Ping
  - [x] Progress
  - [x] Roots
  - [x] Sampling
  - [x] Prompts
  - [x] Resources
  - [x] Tools
  - [x] Completion
  - [x] Logging
  - [ ] Pagination
- [Example projects](example)
  - [x] [CLJC server using STDIO](example/cljc-server-stdio)
  - [x] [CLJC client using STDIO](example/cljc-client-stdio)
  - [x] [CLJ server using HTTP/SSE](example/clj-server-sse)
  - [ ] CLJ server using Streamable HTTP (PR welcome)

## Usage

See the `README.md` in the `example/cljc-server-stdio/` project to learn:
- how to use this library to make your own MCP server in Clojure, and
- how to develop its components (prompts, resources and tools) via the REPL
while the server is running.

Additionally, see the documentation on CLJDocs or in the `doc/` directory.

## Testing

```shell
npm install
./bin/kaocha --watch
```

## Its place in the AI ecosystem

MCP toolkit aims to be more convenient for the Clojure community than
the official MCP SDKs for Java or Typescript.

It provides utilities to build an MCP server in Clojure(script), but
doesn't provide any prompts, resources or tools to help working on a Clojure codebase.
It is typically used for building general purpose MCP stuffs.

## Other MCP libs

- [MCP Clojure SDK](https://github.com/unravel-team/mcp-clojure-sdk): similar library, discovered after being mostly done implementing this one 😅
- Calva's [Backseat Driver](https://github.com/BetterThanTomorrow/calva-backseat-driver)
- [Clojure MCP](https://github.com/bhauman/clojure-mcp)
- [Modex](https://github.com/theronic/modex)

## License

This project is distributed under the [Eclipse Public License v2.0](LICENSE.txt).

Copyright (c) [Metosin](https://metosin.fi) and contributors.
