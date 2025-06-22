(ns mcp-toolkit.impl.client.handler
  (:require [mcp-toolkit.impl.common :refer [user-callback]]))

(defn ping-handler [context]
  {})

(defn root-list-handler [{:keys [session]}]
  {:roots (-> @session :root-by-uri vals
              (->> (mapv (fn [root]
                           (select-keys root [:uri :name])))))})

(def handler-by-method-post-initialization
  {"ping"                                 ping-handler
   "roots/list"                           root-list-handler
   "sampling/createMessage"               (user-callback :on-sampling-requested)
   "notifications/progress"               (user-callback :on-server-progress)
   "notifications/message"                (user-callback :on-server-log)
   "notifications/prompts/list_changed"   (user-callback :on-server-prompt-list-updated)
   "notifications/resources/updated"      (user-callback :on-server-resource-updated)
   "notifications/resources/list_changed" (user-callback :on-server-resource-list-updated)
   "notifications/tools/list_changed"     (user-callback :on-server-tool-list-updated)})

(def handler-by-method-pre-initialization
  {"ping" ping-handler})
