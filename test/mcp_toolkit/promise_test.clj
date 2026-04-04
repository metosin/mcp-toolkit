(ns mcp-toolkit.promise-test
  "Tests for mcp-toolkit.impl.promise — the cross-runtime promise abstraction.
   JVM tests only (babashka verified separately via bb -e)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [mcp-toolkit.impl.promise :as p]))

;; Helpers mirroring the shim's actual behavior
(defn await!
  "Block on a promise/Future, rethrowing any wrapped exception."
  ([promise] (await! promise 5000))
  ([promise timeout-ms]
   (try
     (.get ^java.util.concurrent.Future promise timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS)
     (catch java.util.concurrent.ExecutionException e
       (throw (.getCause e))))))

;; ── Create ────────────────────────────────────────────────────────────────

(deftest create-resolves-test
  (testing "create resolves when resolve fn is called"
    (let [result (p/create (fn [resolve _reject] (resolve 42)))]
      (is (= 42 (await! result))))))

(deftest create-rejects-test
  (testing "create rejects when reject fn is called"
    (let [result (p/create (fn [_resolve reject] (reject (ex-info "boom" {}))))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"boom" (await! result))))))

(deftest create-exception-in-fn-test
  (testing "create catches exceptions thrown in f"
    (let [result (p/create (fn [_ _] (throw (ex-info "oops" {}))))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"oops" (await! result))))))

;; ── Then ──────────────────────────────────────────────────────────────────

(deftest then-chains-success-test
  (testing "then chains success callback"
    (let [result (-> (p/create (fn [resolve _] (resolve 10)))
                     (p/then inc)
                     (p/then #(* % 3)))]
      (is (= 33 (await! result))))))

(deftest then-passes-rejection-through-test
  (testing "then passes rejection through when no on-reject handler"
    (let [result (-> (p/create (fn [_ reject] (reject (ex-info "fail" {}))))
                     (p/then inc))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"fail" (await! result))))))

;; The 3-arg then composes then+catch for the JVM backend
(deftest then-with-on-reject-test
  (testing "then with on-reject calls on-reject instead of rejecting"
    (let [result (-> (p/create (fn [_ reject] (reject (ex-info "fail" {}))))
                     (p/then (fn [_x] (throw (ex-info "should not reach" {})))
                             (fn [_e] :recovered))
                     (p/then identity))]
      (is (= :recovered (await! result))))))

;; ── Catch ─────────────────────────────────────────────────────────────────

(deftest catch-catches-rejection-test
  (testing "catch catches rejection"
    (let [result (-> (p/create (fn [_ reject] (reject (ex-info "caught" {}))))
                     (p/catch (fn [_e] :handled)))]
      (is (= :handled (await! result))))))

(deftest catch-passes-success-through-test
  (testing "catch passes success through"
    (let [result (-> (p/create (fn [resolve _] (resolve :ok)))
                     (p/catch (fn [_e] :handled)))]
      (is (= :ok (await! result))))))

;; ── Handle ────────────────────────────────────────────────────────────────

(deftest handle-success-case-test
  (testing "handle success: value present, error nil"
    (let [result (-> (p/create (fn [resolve _] (resolve 7)))
                     (p/handle (fn [v e] {:value v :error e})))]
      (is (= {:value 7 :error nil} (await! result))))))

(deftest handle-error-case-test
  (testing "handle error: value nil, error present"
    (let [r (-> (p/create (fn [_ reject] (reject (ex-info "handled-err" {}))))
                (p/handle (fn [v e] {:value v :error e}))
                (await!))]
      (is (nil? (:value r)))
      (is (instance? Exception (:error r))))))

;; ── All ───────────────────────────────────────────────────────────────────

(deftest all-resolves-all-test
  (testing "all resolves when all promises resolve"
    (let [result (p/all [(p/resolved 1) (p/resolved 2) (p/resolved 3)])]
      (is (= [1 2 3] (await! result))))))

(deftest all-rejects-one-test
  (testing "all rejects when any promise rejects"
    (let [result (p/all [(p/resolved 1) (p/rejected (ex-info "one fail" {})) (p/resolved 3)])]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"one fail" (await! result))))))

(deftest all-empty-test
  (testing "all with empty collection returns empty vector"
    (let [result (p/all [])]
      (is (= [] (await! result))))))

;; ── Timeout ───────────────────────────────────────────────────────────────

(deftest timeout-resolves-before-deadline-test
  (testing "timeout resolves when promise finishes before deadline"
    (let [result (-> (p/resolved :fast) (p/timeout 1000))]
      (is (= :fast (await! result))))))

(deftest timeout-rejects-after-deadline-test
  (testing "timeout rejects when promise doesn't finish before deadline"
    (let [result (-> (java.util.concurrent.CompletableFuture.) ; never completes
                     (p/timeout 50))]
      (is (thrown? java.util.concurrent.TimeoutException (await! result 500))))))

;; ── Resolved / Rejected ───────────────────────────────────────────────────

(deftest resolved-test
  (testing "resolved creates an already-resolved promise"
    (is (= 99 (await! (p/resolved 99))))))

(deftest rejected-test
  (testing "rejected creates an already-rejected promise"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"already-failed"
                          (await! (p/rejected (ex-info "already-failed" {})))))))

;; ── Property-based Tests ─────────────────────────────────────────────────

(defspec prop-then-chain-is-correct 20
  (prop/for-all [n gen/small-integer]
                (try
                  (let [result (-> (p/resolved n) (p/then inc) (p/then #(* % 2)) (await!))]
                    (= (* 2 (inc n)) result))
                  (catch Throwable _ false))))

(defspec prop-catch-handles-errors 20
  (prop/for-all [_s (gen/fmap str (gen/resize 5 gen/string-alphanumeric))]
                (try
                  (let [result (-> (p/rejected (ex-info "prop-err" {}))
                                   (p/catch (constantly :caught))
                                   (await!))]
                    (= :caught result))
                  (catch Throwable _ false))))

(defspec prop-all-preserves-order 20
  (prop/for-all [a gen/small-integer b gen/small-integer c gen/small-integer]
                (try
                  (let [result (await! (p/all [(p/resolved a) (p/resolved b) (p/resolved c)]))]
                    (= result [a b c]))
                  (catch Throwable _ false))))

(defspec prop-then-identity-is-neutral 20
  (prop/for-all [x gen/small-integer]
                (try
                  (let [original  (await! (p/resolved x))
                        with-then (await! (-> (p/resolved x) (p/then identity)))]
                    (= original with-then))
                  (catch Throwable _ false))))
