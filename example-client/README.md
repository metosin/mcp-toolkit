# Example client

This project is here to get you started and demonstrates how to use the mcp-toolkit library.

The client launches the JVM example-server when started and connects to it via its standard I/O.

## Running the client on the JVM

```shell
clojure -M:mcp-client
```

## Running the client on NodeJS

Launch a nREPL server:
```shell
npx shadow-cljs node-repl
```

or directly compile and run:
```shell
npm install
npx shadow-cljs compile :node-client
node out/node-client.js
```

### License

This project is distributed under the [Eclipse Public License v2.0](LICENSE.txt).

Copyright (c) [Metosin](https://metosin.fi) and contributors.
