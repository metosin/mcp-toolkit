## The context hashmap

The context hashmap contains:
- The session atom
- Some I/O related functions which are use-case specific and that the user has to provide.
- The `:send-message` function 
- The `:close-connection` function

The context hashmap is also used for passing the current message to be processed to the event handlers.

### Context vs. session

The difference between the `context` and the `session` is that the `session` is a mutable state (an atom) being shared by all the event handlers,
while the `context` is an immutable Clojure value contains contextual information, like the current message being processed.

You can add your own data in either the `context` or the `session` if it makes sense for you,
all the message handlers and the user callbacks are called with the context as their first (and only) argument.
