(ns ^:no-doc mcp-toolkit.impl.meta-support
  "Support for _meta field in MCP protocol 2025-06-18.
   
   The _meta field is an optional field that can be included in various message types
   to carry metadata. This namespace provides utilities for handling _meta fields.")

(defn with-meta-field
  "Adds an optional _meta field to a message or data structure if meta is provided.
   
   Args:
     data - The data structure to potentially add _meta to
     meta - Optional metadata map to include
   
   Returns:
     The data with _meta field added if meta is non-nil"
  [data meta]
  (if (and meta (map? meta))
    (assoc data :_meta meta)
    data))

(defn extract-meta
  "Extracts the _meta field from a message or data structure.
   
   Args:
     data - The data structure containing potential _meta field
   
   Returns:
     The _meta field value or nil if not present"
  [data]
  (get data :_meta))

(defn strip-meta
  "Removes the _meta field from a message or data structure.
   
   Args:
     data - The data structure potentially containing _meta field
   
   Returns:
     The data without the _meta field"
  [data]
  (dissoc data :_meta))

(defn merge-meta
  "Merges additional metadata into existing _meta field.
   
   Args:
     data - The data structure potentially containing _meta field
     additional-meta - Additional metadata to merge
   
   Returns:
     The data with merged _meta field"
  [data additional-meta]
  (if (and additional-meta (map? additional-meta))
    (update data :_meta merge additional-meta)
    data))

(defn has-meta?
  "Checks if a data structure has a _meta field.
   
   Args:
     data - The data structure to check
   
   Returns:
     true if _meta field exists, false otherwise"
  [data]
  (contains? data :_meta))
