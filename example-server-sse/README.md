# Example HTTP Server

This is an example implementation demonstrating how to use mcp-toolkit with
Server-Sent Events (SSE) transport as defined by the [2024-11-05 MCP spec][old-spec].

It showcases the HTTP SSE transport capability of MCP servers, allowing MCP
clients to connect via HTTP endpoints instead of stdio.

The implementation uses [http-kit][http-kit] for the web server and
[reitit][reitit] for routing.

Session state is managed with atoms. There is one session map per active SSE
connection.

The transport handles MCP JSON-RPC messages via POST requests to the
`/messages/:session-id` endpoint. And it streams back responses to the client
via SSE on the `/sse` endpoint.

The example includes protection against dns-rebinding attacks, but includes no
authorization.

[http-kit]: https://github.com/http-kit/http-kit
[reitit]: https://github.com/metosin/reitit
[old-spec]: https://modelcontextprotocol.io/specification/2024-11-05/basic/transports

## Usage

Start the MCP server with:

``` shell
clojure -X:mcp-server
```

### MCP Inspector 

1. Make sure that the MCP server is running (see previous section).
2. Run the MCP inspector: `npx @modelcontextprotocol/inspector`.
3. A link to the MCP inspector with the query param `MCP_PROXY_AUTH_TOKEN` is shown, open it in a browser.
4. In the UI, set the "Transport Type to the `SSE` list item and the URL to `http://127.0.0.1:7925/sse`.
5. Press the "Connect" button.

### Claude Code

Add the server's connection settings to Claude's configuration:

``` shell
claude mcp add toolkit-sse --transport sse http://127.0.0.1:7925/sse
```

Then, make sure that your MCP server runs before Claude Code is started.

### Claude Desktop

At the time of writing this, Claude Desktop doesn't support directly connecting to MCP servers via SSE.
Let us know if it changes.

## License

This project is distributed under the [Eclipse Public License v2.0](../LICENSE.txt).

Copyright © 2025 [Casey Link](https://casey.link)
Copyright © 2025 [Metosin](https://metosin.fi) and contributors.
