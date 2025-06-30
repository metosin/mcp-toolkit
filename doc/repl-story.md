## REPL story

When your client or your server is running, connect to it via a REPL using the method of your choice.

On the server side, you can register or unregister resources using:
- `add-prompt`, `remove-prompt`, `add-resource`, `remove-resource`, `add-tool`, `remove-tool`, `set-resource-templates` and `set-resource-uri-complete-fn`
  which will mutate the session atom and notify the client about the changes.

On the client side, you can do the same using `add-root` and `remove-root`.

For all of those above functions, you need to pass the context as the first argument.
To make it accessible from the root level in your Clojure program, depending on your setup, it may be handing to have a root-level atom referring to it.
