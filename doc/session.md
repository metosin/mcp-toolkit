## The session atom

A session is typically created using the function `mcp-toolkit.client/create-session` or `mcp-toolkit.server/create-session`.

It contains:
- The `:initialized` boolean, becomes true after the initial handshake between the client and server.
- The `:protocol-version` used, initialized after the initial handshake.
- `:client-capabilities` / `:server-capabilities` listing what features the remote site supports.
- Some data about the ongoing Remote Method Calls, waiting for a response.
- The locally registered resources and a maintained collection of the resources available on the remote site.
- The callbacks starting with `:on-`, triggered on events, some of them directly being some types of messages.
  Some of them are defaulting to a built-in behavior which maintain the collection of resources available on the remote site.

The session atom can be modified from the message handlers.
