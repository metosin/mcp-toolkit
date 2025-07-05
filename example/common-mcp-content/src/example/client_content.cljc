(ns example.client-content
  (:require [mcp-toolkit.client :as client]
            [promesa.core :as p]))

;; Example of content for an MCP client: Roots and sampling handler.

(def roots
  [{:uri "file:///home/user/projects/my-root"
    :name "My project root"}])

(defn sampling-handler [{:keys [session message] :as context}]
  {:role "assistant"
   :content {:type "text"
             :text "You are absolutely right, and the answer is 42."}
   :model "The Hitchhiker's Guide to the Galaxy"
   :stopReason "endTurn"})
