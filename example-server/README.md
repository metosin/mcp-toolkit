# Example server

This project is here to get you started and demonstrates how to use the mcp-toolkit library.

## Configuring Claude Desktop

The simplest way to get started is to make Claude Desktop launch your server at its startup time.

The communication is happening via the standard I/O.

Configuration example for file located at
`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS:
```json
{
  "mcpServers": {
    "example-mcp-server": {
      "command": "/bin/sh",
      "args": [
        "-c",
        "cd <example-mcp-server-path> && clojure -X:mcp-server"
      ]
    }
  }
}
```

This example server also starts an nREPL server, so after starting Claude Desktop you can
connect to the MCP server via a standard remote REPL connection.

The nREPL server is stopped when Claude Desktop closes your MCP server's
standard input channel, this usually happens when Claude Desktop is closed.

## Running the MCP server and the REPL in a Docker container

In the mcp-toolkit project's root directory, build the container's image:

```shell
docker-compose build
```

It's important to do it first, otherwise the output of the build will be received
by the MCP client and interpreted as JSON-RPC, which is wrong.

In `claude_desktop_config.json`, change the config to:

```json
{
  "mcpServers": {
    "example-mcp-server": {
      "command": "/bin/sh",
      "args": [
        "-c",
        "cd <mcp-toolkit-lib-path> && docker-compose run --service-ports --rm mcp-server clojure -X:mcp-server '{:bind \"0.0.0.0\"}'"
      ]
    }
  }
}
```

.. then restart Claude Desktop.

### Testing

```shell
npx @modelcontextprotocol/inspector clojure -X:mcp-server
```

## Running the server on NodeJS

Launch a nREPL server:
```shell
npx shadow-cljs node-repl
```

or directly compile and run:
```shell
npm install
npx shadow-cljs compile :node-server
node out/node-server.js
```

### Testing

```shell
npx @modelcontextprotocol/inspector node out/node-server.js
```

## Troubleshooting

It can take a while until Claude Desktop asks the MCP server for its updated list
of prompts, resources and tools. The update can be manually triggered by clicking on
the button "Connect apps" in Claude Desktop's UI.

## License

This project is distributed under the [Eclipse Public License v2.0](LICENSE.txt).

Copyright (c) [Metosin](https://metosin.fi) and contributors.
