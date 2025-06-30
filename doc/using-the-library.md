## How to use the library

The process is best described in the `example-server` and `example-client` projects
which are well commented.

It boils down to this:
1. Create a session with all your resources and customization in it, and put it into an atom.
2. Create a context hashmap with all your I/O related hooks in it.
3. When you receive a message somehow, transform it into a Clojure data structure and call `mcp-toolkit.json-rpc/handle-message` with your message in the context: `(json-rpc/handle-message (-> context (assoc :message message))`
4. If you are implementing a client, send the first message of the initial handshake by calling `(client/send-first-handshake-message context)`
5. Watch it work and enjoy.
