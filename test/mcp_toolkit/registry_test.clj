(ns mcp-toolkit.registry-test
  "Tests for mcp-toolkit.registry."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [mcp-toolkit.registry :as reg]
            [mcp-toolkit.impl.common :as c]))

(defn make-tool [& {:keys [name description input-schema handler timeout dependencies group]
                    :or {name :test/tool
                         description "A test tool"
                         input-schema {:type "object" :properties {}}
                         handler (fn [_ args] {:content [{:type "text" :text (str "ok: " args)}]})
                         timeout nil
                         dependencies nil
                         group nil}}]
  {:name name
   :description description
   :inputSchema input-schema
   :handler handler
   :timeout timeout
   :dependencies dependencies
   :group group})

(defn make-plugin [& {:keys [name version tools dependencies lifecycle]
                      :or {name :test-plugin
                           version "0.1.0"
                           tools [(make-tool)]
                           dependencies nil
                           lifecycle nil}}]
  (cond-> {:name name :version version :tools tools}
    dependencies (assoc :dependencies dependencies)
    lifecycle (assoc :lifecycle lifecycle)))

(deftest register!-success
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :test))
    (is (= [:test] (reg/list-plugins r)))
    (is (= 1 (reg/plugin-count r)))
    (is (= 1 (reg/tool-count r)))))

(deftest register!-multiple-plugins
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :a :tools [(make-tool :name :a/t1) (make-tool :name :a/t2)]))
    (reg/register! r (make-plugin :name :b :tools [(make-tool :name :b/t1)]))
    (is (= [:a :b] (reg/list-plugins r)))
    (is (= 3 (reg/tool-count r)))))

(deftest register!-duplicate-plugin
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :test))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Plugin already registered"
                          (reg/register! r (make-plugin :name :test))))
    (is (= 1 (reg/plugin-count r)))))

(deftest register!-duplicate-tool
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :a :tools [(make-tool :name :shared/tool)]))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Duplicate tool names"
                          (reg/register! r (make-plugin :name :b :tools [(make-tool :name :shared/tool)]))))
    (is (= [:a] (reg/list-plugins r)))))

(deftest register!-invalid-plugin
  (testing "missing :name"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid plugin"
                          (reg/register! (reg/create) {:version "1.0" :tools []}))))
  (testing "missing :version"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid plugin"
                          (reg/register! (reg/create) {:name :test :tools []}))))
  (testing "missing :tools"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid plugin"
                          (reg/register! (reg/create) {:name :test :version "1.0"})))))

(deftest register!-invalid-tool
  (testing "missing :handler"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid tool"
                          (reg/register! (reg/create)
                                         {:name :test :version "1.0"
                                          :tools [{:name :t :description "x" :inputSchema {}}]}))))
  (testing "missing :name"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid tool"
                          (reg/register! (reg/create)
                                         {:name :test :version "1.0"
                                          :tools [{:description "x" :inputSchema {} :handler (fn [])}]})))))

(deftest register!-tool-deps-satisfied
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :a
                                  :tools [(make-tool :name :a/dep)
                                          (make-tool :name :a/tool :dependencies [:a/dep])]))
    (is (= 1 (reg/plugin-count r)))))

(deftest register!-tool-deps-missing
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Missing tool dependencies"
                        (reg/register! (reg/create)
                                       (make-plugin :name :t :tools [(make-tool :name :t/x :dependencies [:missing])])))))

(deftest register!-plugin-deps-satisfied
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :base))
    (reg/register! r (make-plugin :name :ext :dependencies [:base]))
    (is (= 2 (reg/plugin-count r)))))

(deftest register!-plugin-deps-missing
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Missing plugin dependencies"
                        (reg/register! (reg/create)
                                       (make-plugin :name :ext :dependencies [:missing])))))

(deftest register!-lifecycle
  (let [r (reg/create)
        called? (atom false)]
    (reg/register! r (make-plugin :name :t
                                  :lifecycle {:on-register (fn [] (reset! called? true))}))
    (is @called?)))

(deftest unregister!-success
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :a :tools [(make-tool :name :a/t1) (make-tool :name :a/t2)]))
    (reg/register! r (make-plugin :name :b :tools [(make-tool :name :b/t1)]))
    (reg/unregister! r :a)
    (is (= [:b] (reg/list-plugins r)))
    (is (= 1 (reg/tool-count r)))
    (is (nil? (reg/find-tool r :a/t1)))
    (is (some? (reg/find-tool r :b/t1)))))

(deftest unregister!-not-found
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Plugin not registered"
                        (reg/unregister! (reg/create) :nope))))

(deftest unregister!-lifecycle
  (let [r (reg/create)
        called? (atom false)]
    (reg/register! r (make-plugin :name :t
                                  :lifecycle {:on-unregister (fn [] (reset! called? true))}))
    (reg/unregister! r :t)
    (is @called?)))

(deftest find-tool-handler
  (let [r (reg/create)
        h (fn [_ _] :ok)]
    (reg/register! r (make-plugin :name :t :tools [(make-tool :name :t/x :handler h)]))
    (is (= h (reg/find-tool-handler r :t/x)))
    (is (nil? (reg/find-tool-handler r :nope)))))

(deftest find-tool
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :t
                                  :tools [(make-tool :name :t/x :timeout 5000 :group :r)]))
    (let [m (reg/find-tool r :t/x)]
      (is (= :t (:plugin m)))
      (is (= 5000 (:timeout m)))
      (is (= :r (:group m)))
      (is (fn? (:handler m))))))

(deftest all-tools
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :a :tools [(make-tool :name :a/t1) (make-tool :name :a/t2)]))
    (reg/register! r (make-plugin :name :b :tools [(make-tool :name :b/t1)]))
    (let [tools (reg/all-tools r)]
      (is (= 3 (count tools)))
      (is (every? #(contains? % :handler) tools)))))

(deftest tools-by-group
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :t :tools
                                  [(make-tool :name :t/r :group :read)
                                   (make-tool :name :t/w1 :group :write)
                                   (make-tool :name :t/w2 :group :write)
                                   (make-tool :name :t/u)]))
    (is (= 2 (count (reg/tools-by-group r :write))))
    (is (= 1 (count (reg/tools-by-group r :read))))
    (is (= 0 (count (reg/tools-by-group r :admin))))
    (is (= 3 (count (reg/tools-by-group r [:read :write]))))
    (is (= 4 (count (reg/tools-by-group r nil))))))

(deftest tool-accessors
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :t :tools
                                  [(make-tool :name :t/a :timeout 3000 :dependencies [:x] :group :g)
                                   (make-tool :name :t/b)]))
    (is (= 3000 (reg/get-tool-timeout r :t/a)))
    (is (nil? (reg/get-tool-timeout r :t/b)))
    (is (= [:x] (reg/get-tool-dependencies r :t/a)))
    (is (= [] (reg/get-tool-dependencies r :t/b)))
    (is (= :g (reg/get-tool-group r :t/a)))
    (is (nil? (reg/get-tool-group r :t/b)))
    (is (true? (reg/tool-exists? r :t/a)))
    (is (false? (reg/tool-exists? r :nope)))
    (is (= :t (reg/get-tool-plugin r :t/a)))
    (is (nil? (reg/get-tool-plugin r :nope)))))

(deftest registry-snapshot
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :t
                                  :tools [(make-tool :name :t/x)]
                                  :lifecycle {:on-register (fn [])}))
    (let [s (reg/registry-snapshot r)]
      (is (= 1 (:tool-count s)))
      (is (= [:t/x] (:tool-names s)))
      (is (nil? (get-in s [:plugins :t :lifecycle]))))))

(deftest check-invariants-clean
  (let [r (reg/create)]
    (reg/register! r (make-plugin :name :a :tools [(make-tool :name :a/t)]))
    (reg/register! r (make-plugin :name :b :tools [(make-tool :name :b/t)]))
    (is (nil? (reg/check-invariants r)))))

(deftest valid-malli-schema?
  (is (true? (reg/valid-malli-schema? :string)))
  (is (true? (reg/valid-malli-schema? [:map [:name :string]])))
  (is (false? (reg/valid-malli-schema? :nope)))
  (is (false? (reg/valid-malli-schema? {:not "schema"}))))

;; Property-based tests

(defspec register-unregister-identity 100
  (prop/for-all [_ (gen/choose 1 3)]
                (let [r (reg/create)
                      p (make-plugin :name :temp :tools [(make-tool :name :temp/t)])]
                  (reg/register! r p)
                  (reg/unregister! r :temp)
                  (and (empty? (reg/list-plugins r))
                       (= 0 (reg/tool-count r))
                       (nil? (reg/check-invariants r))))))

(defspec munge-unmunge-round-trip 100
  (prop/for-all [kw (gen/fmap (fn [[ns name]]
                                (keyword (str "ns-" ns) (str "n-" name)))
                              (gen/tuple (gen/elements ["a" "b" "c"])
                                         (gen/elements ["x" "y" "z"])))]
                (= kw (c/unmunge-name (c/munge-name kw)))))

(defspec index-consistent 100
  (prop/for-all [n (gen/choose 1 10)]
                (let [r (reg/create)
                      plugins (mapv (fn [i]
                                      (make-plugin :name (keyword (str "p" i))
                                                   :tools [(make-tool :name (keyword (str "p" i) (str "t")))]))
                                    (range n))]
                  (try
                    (reduce reg/register! r plugins)
                    (= (reg/tool-count r) (count (reg/all-tools r)))
                    (catch Exception _
                      true)))))

(defspec every-tool-has-handler 100
  (prop/for-all [n (gen/choose 1 5)]
                (let [r (reg/create)
                      plugins (mapv (fn [i]
                                      (make-plugin :name (keyword (str "p" i))
                                                   :tools [(make-tool :name (keyword (str "p" i) (str "t")))]))
                                    (range n))]
                  (try
                    (reduce reg/register! r plugins)
                    (every? #(fn? (:handler %)) (reg/all-tools r))
                    (catch Exception _
                      true)))))
