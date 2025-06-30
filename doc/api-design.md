## API Design

MCP-toolkit is used in a client-server architecture, the client typically being an agentic AI,
and the server typically being an MCP server in Clojure.

The namespace `mcp-toolkit.client` is for implementing a MCP client, it is designed to communicate with a MCP server.
The namespace `mcp-toolkit.server` is for implementing a MCP server, it is designed to communicate with a MCP client.
If you want to implement a MCP proxy, you can use both.

The API design is the same in both namespaces:
- The state representing the communication with the remote site is in an atom called `session`.
- A hashmap `context` contains the `session` and a few other things related to interfacing with the transport layout.
- The `context` is passed as the first parameter of most of functions in the API.
- The `session` contains some callbacks which are called when receiving certain kind of messages from the MCP protocol.
  By default some of the callbacks are pointing to provided function which implement a default behavior.
  If the user wants to customize the behavior, s/he can do it at the creation of the session value.
- All the message handlers are assumed to either return a value to be immediately sent as `:result` to the caller, or
  to return a Promesa promise which will resolve to that value later, asynchronously.
