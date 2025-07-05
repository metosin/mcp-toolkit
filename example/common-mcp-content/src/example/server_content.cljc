(ns example.server-content
  (:require [clojure.string :as str]
            [mcp-toolkit.server :as server]
            [promesa.core :as p]))

;; Example of content for an MCP server: Prompts, resources, tools.

(def talk-like-pirate-prompt
  {:name "pirate_mode_prompt"
   :description "Talk like a pirate prompt"
   :arguments [{:name "expressions"
                :description "Comma-separated expressions"
                :required false}]
   :complete-fn (fn [context name value]
                  (when (= name "expressions")
                    (when-not (str/includes? name "!")
                      {:completion {:values [(str value "!")
                                             (str value "~#!")
                                             (str value "!#@!!")]
                                    :total 3
                                    :hasMore false}})))
   :prompt-fn (fn [context {:keys [expressions]}]
                {:description (str "A talk-like-a-pirate prompt which includes the expressions: " expressions)
                 :messages [{:role "user"
                             :content {:type "text"
                                       :text (->> ["You are a sea pirate and you need to talk to the user in a tone that reassembles talk-like-a-pirate style."
                                                   (when-not (str/blank? expressions)
                                                     (str "You also need to randomly use the following expressions every few sentences: " expressions "."))
                                                   (str "Start by introducing yourself in a spectacular way and talk about your pet parrot sitting on your shoulder. "
                                                        "Ask the user to choose a new and creative name for the parrot.")]
                                                  (filter some?)
                                                  (str/join " "))}}]})})

(def hello-doc-resource
  {:uri "file:///doc/hello.md"
   :name "hello.md"
   :description "First part of the \"hello world\" resources"
   :mimeType "text/markdown; charset=UTF-8"
   ;;:blob ,,,
   :text "Hello"})

(def world-doc-resource
  {:uri "file:///doc/world.md"
   :name "world.md"
   :description "Second part of the \"hello world\" resources"
   :mimeType "text/markdown; charset=UTF-8"
   ;;:blob ,,,
   :text "world!"})

(def my-resource-templates
  [{:uriTemplate "file:///doc/{path}"
    :name "Documentation files"
    :description "The documentation files"
    :mimeType "text/markdown; charset=UTF-8"}])

(defn my-resource-uri-complete-fn [context uri name value]
  (when (and (= uri "file:///doc/{path}")
             (= name "path"))
    (let [paths ["hello.md" "world.md"]
          values (filterv (fn [path] (str/starts-with? path value)) paths)]
      {:completion {:values (take 100 values)
                    :total (count values)
                    :hasMore (> (count values) 100)}})))

(def parentify-tool
  {:name "parentify"
   :description "Parentify a text: wraps a text within parenthesis."
   :inputSchema {:type "object"
                 :properties {:text {:type "string"
                                     :description "the text to be parentified"}}
                 :required [:text]}
   :tool-fn (fn [context arguments]
              (-> (p/let [text (str "(" (:text arguments) ")")
                          _ (p/delay 1000)
                          _ (server/notify-progress context {:progress 1
                                                             :total 3
                                                             :message "thinking ..."})
                          _ (p/delay 1000)
                          _ (server/notify-progress context {:progress 2
                                                             :total 3
                                                             :message "thinking harder ..."})
                          _ (when @(:is-cancelled context)
                              (throw (ex-info "tool was cancelled" {:note "too bad, was almost done"})))

                          _ (p/delay 1000)]
                    {:content [{:type "text"
                                :text text}]
                     :isError false})
                  (p/catch (fn [exception]
                             {:content [{:type "text"
                                         :text (str "Something went wrong: " (ex-message exception))}]
                              :isError true}))))})
