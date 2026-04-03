(ns mcp-toolkit.registry
  "Plugin registry for MCP servers.

   Tools, prompts, and resources are bundled into plugins
   that can be registered, merged, and validated.

   Design invariants (enforced, not just tested):
   - No duplicate plugin names
   - No duplicate tool names across plugins
   - Every tool has a non-nil :handler
   - Tool dependency declarations are validated at registration
   - The derived tool-index is always consistent with plugins

   All mutations are atomic via swap! — the index is rebuilt
   inside the transaction so it's never stale."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]))

;; ─── Schemas ──────────────────────────────────────────────────────────────

(def tool-schema
  "A tool bundles its schema and handler together.
   Names are namespaced keywords: :art19/list-episodes.
   At the MCP protocol boundary these are munged to strings:
   \"art19__list_episodes\"."
  [:map {:closed true}
   [:name :keyword]                              ;; :art19/list-episodes
   [:description :string]
   [:inputSchema :map]                           ;; JSON Schema object
   [:handler [:fn fn?]]
   [:timeout {:optional true} :int]                   ;; per-tool timeout in ms
   [:dependencies {:optional true} [:vector :keyword]] ;; tools this depends on
   [:group {:optional true} :keyword]])               ;; for filtered tools/list

(def lifecycle-schema
  "Optional plugin lifecycle hooks."
  [:map {:closed true}
   [:on-register {:optional true} [:fn fn?]]
   [:on-unregister {:optional true} [:fn fn?]]
   [:on-error {:optional true} [:fn fn?]]])

(def plugin-schema
  "A plugin is a bundle of tools, prompts, and resources.
   The :config key must be a valid Malli schema (or nil)."
  [:map {:closed true}
   [:name :keyword]                              ;; :art19
   [:version :string]                            ;; "1.0.0"
   [:tools [:vector tool-schema]]
   [:prompts {:optional true} [:vector :any]]
   [:resources {:optional true} [:vector :any]]
   [:dependencies {:optional true} [:vector :keyword]] ;; other plugins this needs
   [:lifecycle {:optional true} lifecycle-schema]
   [:config {:optional true} [:or nil? :any]]])       ;; validated separately

;; Pre-built validators for boundary checks
(def valid-tool? (m/validator tool-schema))
(def valid-plugin? (m/validator plugin-schema))
(def explain-tool (m/explainer tool-schema))
(def explain-plugin (m/explainer plugin-schema))

(defn valid-malli-schema?
  "Check if a value is a valid Malli schema."
  [v]
  (try
    (m/schema v)
    true
    (catch #?(:clj Throwable :cljs js/Error) _
      false)))

;; ─── Registry Internal Structure ──────────────────────────────────────────
;;
;; {:plugins   {:art19 {...} :podhome {...}}     ;; plugin-name → plugin
;;  :tool-index {:art19/list-episodes             ;; tool-name → metadata
;;               {:handler ...
;;                :plugin :art19
;;                :timeout 5000
;;                :dependencies [...]
;;                :group :read-only}
;;               :podhome/get-show
;;               {:handler ...
;;                :plugin :podhome
;;                :timeout nil
;;                :dependencies nil
;;                :group nil}}}

(defn create
  "Create an empty registry."
  []
  (atom {:plugins {} :tool-index {}}))

(defn- collect-tool-names
  "Return a set of all tool names currently in the registry."
  [tool-index]
  (set (keys tool-index)))

(defn- build-tool-index-entry
  "Build the index entry for a single tool."
  [tool plugin-name]
  {(:name tool) {:handler (:handler tool)
                 :plugin plugin-name
                 :timeout (:timeout tool)
                 :dependencies (:dependencies tool)
                 :group (:group tool)}})

(defn- merge-tool-index
  "Merge new tools into the existing index. Returns the new index."
  [existing-index tools plugin-name]
  (reduce (fn [idx tool]
            (merge idx (build-tool-index-entry tool plugin-name)))
          existing-index
          tools))

;; ─── Registration ─────────────────────────────────────────────────────────

(defn register!
  "Register a plugin in the registry.

   Validates the plugin against plugin-schema.
   Checks for plugin name collisions.
   Checks for tool name collisions against ALL existing tools.
   Validates that declared tool dependencies exist.
   Builds the derived tool-index atomically.
   Runs :on-register lifecycle hook if present.

   Throws on any validation failure — the registry is never left
   in a partially-registered state.

   Returns the registry (for threading)."
  [registry plugin]
  (let [pname (:name plugin)]
    ;; Validate plugin shape before entering swap!
    (when-not (valid-plugin? plugin)
      (let [errors (explain-plugin plugin)]
        (throw (ex-info "Invalid plugin"
                        {:plugin pname
                         :errors (me/humanize errors)}))))

    ;; Validate config schema if present
    (when-let [cfg (:config plugin)]
      (when-not (valid-malli-schema? cfg)
        (throw (ex-info "Invalid plugin config schema"
                        {:plugin pname
                         :config cfg}))))

    ;; Validate each tool
    (doseq [tool (:tools plugin)]
      (when-not (valid-tool? tool)
        (let [errors (explain-tool tool)]
          (throw (ex-info (str "Invalid tool in plugin " pname)
                          {:plugin pname
                           :tool (:name tool)
                           :errors (me/humanize errors)})))))

    ;; Atomic: check collisions + validate deps + build index
    (swap! registry
           (fn [{:keys [plugins tool-index]}]
             (let [;; 1. Tool name collision check
                   existing-names (collect-tool-names tool-index)
                   new-tools (:tools plugin)
                   new-names (set (map :name new-tools))
                   collisions (set/intersection existing-names new-names)

                   ;; 2. Tool dependency validation
                   declared-deps (->> new-tools
                                      (mapcat :dependencies)
                                      (keep identity)
                                      set)
                   missing-deps (set/difference declared-deps
                                                (set/union existing-names new-names))

                   ;; 3. Plugin dependency validation
                   plugin-deps (:dependencies plugin)
                   missing-plugin-deps (when plugin-deps
                                         (remove #(contains? plugins %) plugin-deps))]

               ;; Check: plugin name collision
               (when (contains? plugins pname)
                 (throw (ex-info (str "Plugin already registered: " pname)
                                 {:plugin pname})))

               ;; Check: tool name collisions
               (when (seq collisions)
                 (throw (ex-info (str "Duplicate tool names: "
                                      (str/join ", " (map str collisions)))
                                 {:collisions collisions
                                  :plugin pname})))

               ;; Check: tool dependencies
               (when (seq missing-deps)
                 (throw (ex-info (str "Missing tool dependencies: "
                                      (str/join ", " (map str missing-deps)))
                                 {:missing missing-deps
                                  :plugin pname})))

               ;; Check: plugin dependencies
               (when (seq missing-plugin-deps)
                 (throw (ex-info (str "Missing plugin dependencies: "
                                      (str/join ", " (map str missing-plugin-deps)))
                                 {:missing missing-plugin-deps
                                  :plugin pname})))

               ;; Build new state
               {:plugins (assoc plugins pname plugin)
                :tool-index (merge-tool-index tool-index new-tools pname)})))

    ;; Run lifecycle hook (outside swap! — side effects shouldn't be in transactions)
    (when-let [hook (get-in plugin [:lifecycle :on-register])]
      (hook))

    registry))

;; ─── Unregistration ──────────────────────────────────────────────────────

(defn unregister!
  "Remove a plugin from the registry.

   Removes the plugin and all its tools from the index.
   Runs :on-unregister lifecycle hook if present.

   Throws if the plugin is not registered.
   Returns the registry (for threading)."
  [registry plugin-name]
  (let [plugin (get-in @registry [:plugins plugin-name])]
    (when-not plugin
      (throw (ex-info (str "Plugin not registered: " plugin-name)
                      {:plugin plugin-name})))

    (swap! registry
           (fn [{:keys [plugins tool-index]}]
             {:plugins (dissoc plugins plugin-name)
              :tool-index (apply dissoc tool-index
                                 (->> tool-index
                                      (filter (fn [[_ v]] (= (:plugin v) plugin-name)))
                                      (map key)))}))

    ;; Run lifecycle hook
    (when-let [hook (get-in plugin [:lifecycle :on-unregister])]
      (hook)))

  registry)

;; ─── Queries ──────────────────────────────────────────────────────────────

(defn get-plugin
  "Get a plugin by name. Returns nil if not found."
  [registry plugin-name]
  (get-in @registry [:plugins plugin-name]))

(defn list-plugins
  "Return a sorted vector of registered plugin names."
  [registry]
  (sort (keys (:plugins @registry))))

(defn plugin-count
  "Return the number of registered plugins."
  [registry]
  (count (:plugins @registry)))

(defn tool-count
  "Return the total number of registered tools."
  [registry]
  (count (:tool-index @registry)))

;; ─── Merged Views ────────────────────────────────────────────────────────

(defn all-tools
  "Return a flat vector of all tool metadata across all plugins.
   Each entry is {:name :art19/list-episodes :handler ... :plugin :art19 ...}.
   O(n) — use find-tool-handler for O(1) lookup of a specific tool."
  [registry]
  (->> @registry
       :tool-index
       (map (fn [[name meta]]
              (assoc meta :name name)))
       (into [])))

(defn all-tools-for-protocol
  "Return tools formatted for the MCP protocol boundary.
   Tool names are munged to strings: :art19/list-episodes → \"art19__list_episodes\".
   Only includes :name, :description, :inputSchema (not :handler)."
  [registry munge-fn]
  (let [munge (or munge-fn str)]
    (->> @registry
         :tool-index
         (map (fn [[name meta]]
                {:name (munge name)
                 :description (:description meta)
                 :inputSchema (:inputSchema meta)}))
         (into []))))

(defn tools-by-plugin
  "Return a map of plugin-name → vector of tool metadata."
  [registry]
  (->> @registry
       :tool-index
       (group-by (fn [[_ meta]] (:plugin meta)))
       (map (fn [[pname entries]]
              [pname (->> entries
                          (map (fn [[name meta]]
                                 (assoc meta :name name)))
                          (into []))]))
       (into {})))

(defn all-prompts
  "Return a flat vector of all prompts across all plugins."
  [registry]
  (->> @registry
       :plugins
       vals
       (mapcat :prompts)
       (filter some?)
       (into [])))

(defn all-resources
  "Return a flat vector of all resources across all plugins."
  [registry]
  (->> @registry
       :plugins
       vals
       (mapcat :resources)
       (filter some?)
       (into [])))

(defn tools-by-group
  "Return tools filtered by group keyword(s).
   If groups is a single keyword, returns tools in that group.
   If groups is a collection, returns tools in any of those groups.
   Returns all tools if groups is nil."
  [registry groups]
  (if (nil? groups)
    (all-tools registry)
    (let [group-set (if (coll? groups) (set groups) #{groups})]
      (->> @registry
           :tool-index
           (filter (fn [[_ meta]]
                     (let [g (:group meta)]
                       (and g (contains? group-set g)))))
           (map (fn [[name meta]]
                  (assoc meta :name name)))
           (into [])))))

;; ─── O(1) Lookup ─────────────────────────────────────────────────────────

(defn find-tool
  "Find a tool by its namespaced keyword name.
   Returns the tool metadata map or nil. O(1) via index.

   Example: (find-tool reg :art19/list-episodes)
   => {:handler ... :plugin :art19 :timeout 5000 :group :read-only}"
  [registry tool-name]
  (get-in @registry [:tool-index tool-name]))

(defn find-tool-handler
  "Find a tool's handler by its namespaced keyword name.
   Returns the handler function or nil. O(1) via index."
  [registry tool-name]
  (get-in @registry [:tool-index tool-name :handler]))

(defn tool-exists?
  "Check if a tool name is registered. O(1)."
  [registry tool-name]
  (contains? (:tool-index @registry) tool-name))

(defn get-tool-plugin
  "Get the plugin name that owns a tool. O(1)."
  [registry tool-name]
  (get-in @registry [:tool-index tool-name :plugin]))

;; ─── Introspection ───────────────────────────────────────────────────────

(defn get-tool-timeout
  "Get the timeout for a tool (in ms). Returns nil if not set."
  [registry tool-name]
  (get-in @registry [:tool-index tool-name :timeout]))

(defn get-tool-dependencies
  "Get the declared dependencies for a tool. Returns empty vector if none."
  [registry tool-name]
  (or (get-in @registry [:tool-index tool-name :dependencies]) []))

(defn get-tool-group
  "Get the group keyword for a tool. Returns nil if not set."
  [registry tool-name]
  (get-in @registry [:tool-index tool-name :group]))

(defn registry-snapshot
  "Return a read-only snapshot of the registry state.
   Useful for debugging or serialization."
  [registry]
  (let [{:keys [plugins tool-index]} @registry]
    {:plugins (->> plugins
                   (map (fn [[pname plugin]]
                          [pname (dissoc plugin :lifecycle)]))
                   (into {}))
     :tool-count (count tool-index)
     :tool-names (sort (keys tool-index))}))

;; ─── Invariant Checks (for testing) ──────────────────────────────────────

(defn check-invariants
  "Verify all registry invariants. Returns nil if all pass,
   or a vector of failure descriptions."
  [registry]
  (let [{:keys [plugins tool-index]} @registry
        failures (atom [])]

    ;; 1. Every plugin has valid shape
    (doseq [[pname plugin] plugins]
      (when-not (valid-plugin? plugin)
        (swap! failures conj (str "Invalid plugin: " pname))))

    ;; 2. Every tool has a non-nil handler
    (doseq [[tname meta] tool-index]
      (when-not (:handler meta)
        (swap! failures conj (str "Tool " tname " has nil handler"))))

    ;; 3. Index count matches total tool count across plugins
    (let [expected-count (->> plugins vals (mapcat :tools) count)
          actual-count (count tool-index)]
      (when (not= expected-count actual-count)
        (swap! failures conj
               (str "Index count mismatch: index has " actual-count
                    " but plugins have " expected-count " tools"))))

    ;; 4. Every tool in index has a valid plugin reference
    (doseq [[tname meta] tool-index]
      (when-not (contains? plugins (:plugin meta))
        (swap! failures conj
               (str "Tool " tname " references non-existent plugin " (:plugin meta)))))

    ;; 5. All declared tool dependencies exist
    (doseq [[tname meta] tool-index]
      (doseq [dep (:dependencies meta)]
        (when-not (contains? tool-index dep)
          (swap! failures conj
                 (str "Tool " tname " depends on missing tool " dep)))))

    ;; 6. All declared plugin dependencies exist
    (doseq [[pname plugin] plugins]
      (doseq [dep (:dependencies plugin)]
        (when-not (contains? plugins dep)
          (swap! failures conj
                 (str "Plugin " pname " depends on missing plugin " dep)))))

    (when (seq @failures)
      @failures)))
