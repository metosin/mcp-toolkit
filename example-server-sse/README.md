# Example HTTP Server

This is an example implementation demonstrating how to use mcp-toolkit with
Server-Sent Events (SSE) transport as defined by the [2024-11-05 MCP spec][old-spec].

It showcases the HTTP SSE transport
capability of MCP servers, allowing MCP clients to connect via HTTP
endpoints instead of stdio.

The implementation uses [http-kit][http-kit] for the web server and [reitit][reitit] for routing.

Session state is managed with atoms. There is one session map per active SSE connection.

The transport handles MCP JSON-RPC messages via POST requests to the `/messages/:session-id` endpoint. And it streams back repsones to the client via SSE on the `/sse` endpoint.

The example includes protection against dns-rebinding attacks, but includes no authorization.

[http-kit]: https://github.com/http-kit/http-kit
[reitit]: https://github.com/metosin/reitit
[old-spec]: https://modelcontextprotocol.io/specification/2024-11-05/basic/transports

## Usage

Start the mcp server with:

``` shell
clojure -X:mcp-server
```

### MCP Inspector 

* Transport type: `SSE`
* URL: `http://127.0.0.1:7925/sse`

### Claude Code

``` shell
claude mcp add example-mcp --transport sse http://127.0.0.1:7925/sse
```

### Claude Desktop 

Configuration example for file located at
`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS:
```json
{
    "mcpServers": {
        "example-server": {
            "type": "sse",
            "url": "http://127.0.0.1:7925/sse"
        }
    }
}
```


## License

This project is distributed under the [Eclipse Public License v2.0](../LICENSE.txt).

Copyright © 2025 [Casey Link](https://casey.link)
Copyright © 2025 [Metosin](https://metosin.fi)
