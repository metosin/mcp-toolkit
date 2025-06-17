(ns mcp-toolkit.client.impl.handler
  (:require [mate.core :as mc]
            [mcp-toolkit.client.core :as client]
            [mcp-toolkit.json-rpc.handler :as json-rpc.handler]
            [mcp-toolkit.json-rpc.message :as json-rpc.message]
            [promesa.core :as p]))

(defn- user-callback [callback-key]
  (fn [context]
    (when-some [callback (-> context :session deref (get callback-key))]
      (callback context))))

(defn ping-handler [context]
  {})

(defn root-list-handler [{:keys [session]}]
  {:roots (-> @session :root-by-uri vals
              (->> (mapv (fn [root]
                           (select-keys root [:uri :name])))))})

(def handler-by-method-post-initialization
  {"ping"                                 ping-handler
   "roots/list"                           root-list-handler
   "notifications/progress"               (user-callback :on-server-progress)
   "notifications/message"                (user-callback :on-server-log)
   "notifications/prompts/list_changed"   (user-callback :on-server-prompt-list-updated)
   "notifications/resources/updated"      (user-callback :on-server-resource-updated)
   "notifications/resources/list_changed" (user-callback :on-server-resource-list-updated)
   "notifications/tools/list_changed"     (user-callback :on-server-tool-list-updated)})

(def handler-by-method-pre-initialization
  {"ping" ping-handler})
