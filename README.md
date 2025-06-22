# MCP Toolkit

This library is a very unofficial MCP SDK in Clojure.

It handles the communication between MCP clients and MCP servers, and attempts to provide
a Clojure-ish experience to developers working on expending the MCP ecosystem.

Status: **"work in progress"**

Tested on Claude Desktop and Claude Code, no problems found for the features implemented.

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
  - [ ] `2025-06-18` (not yet)
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
- Example projects
  - [x] MCP client in CLJC
  - [x] MCP server in CLJC
  - [x] Stdio transport
  - [ ] HTTP with SSE (PR welcome)
  - [ ] Streamable HTTP transport (PR welcome)

## Usage

See the `README.md` in the `example-server/` project to learn:
- how to use this library to make your own MCP server in Clojure, and
- how to develop its components (prompts, resources and tools) via the REPL
while the server is running.

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
