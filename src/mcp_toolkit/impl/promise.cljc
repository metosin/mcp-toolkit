(ns mcp-toolkit.impl.promise
  "Internal promise shim — one API, three backends, 8 functions, 0 call-site changes.

   :clj   → promesa 11             (JVM, virtual threads)
   :bb    → CompletableFuture      (native in bb, zero deps)
   :cljs  → promesa               (ClojureScript, goog.Promise)
   :squint→ js/Promise            (future)

   Swap [promesa.core :as p] → [mcp-toolkit.impl.promise :as p], done."
  #?@(:bb   []
      :clj  [(:require [promesa.core :as p])]
      :cljs [(:require [promesa.core :as p])]))

;; ── Babashka backend (CompletableFuture) ──────────────────────────────────
;; bb is a GraalVM native-image: BiFunction reflection is blocked.
;; Composes .thenApply + .exceptionally (unary Function only).

#?(:bb
   (do
     (defn create
       "Create promise. f receives (resolve reject)."
       [f]
       (let [cf (java.util.concurrent.CompletableFuture.)]
         (try
           (f (fn [v] (.complete cf v))
              (fn [e] (.completeExceptionally cf e)))
           (catch Throwable e (.completeExceptionally cf e)))
         cf))

     (defn then
       "Chain success (and optional error) handler."
       ([promise f]
        (.thenApply ^java.util.concurrent.CompletableFuture promise f))
       ([promise f on-reject]
        ;; Compose unary Functions: thenApply(success) → exceptionally(error)
        (-> ^java.util.concurrent.CompletableFuture promise
            (.thenApply f)
            (.exceptionally #(on-reject %)))))

     (defn catch
       "Catch errors."
       [promise f]
       (.exceptionally ^java.util.concurrent.CompletableFuture promise
                       #(f %)))

     (defn handle
       "Handle success or error — f receives (value error), one is nil.
        Composed from .thenApply + .exceptionally: no BiFunction."
       [promise f]
       (-> ^java.util.concurrent.CompletableFuture promise
           (.thenApply #(f % nil))
           (.exceptionally #(f nil %))))

     (defn all
       "All promises resolve → vector. Any rejects → rejection."
       [promises]
       (if (empty? promises)
         (java.util.concurrent.CompletableFuture/completedFuture [])
         (-> (java.util.concurrent.CompletableFuture/allOf
              (into-array java.util.concurrent.CompletableFuture promises))
             (.thenApply
              (fn [_]
                (mapv #(.get ^java.util.concurrent.CompletableFuture %)
                      promises))))))

     (defn timeout
       "Reject if not resolved within ms."
       ([promise ms]
        (.orTimeout ^java.util.concurrent.CompletableFuture promise
                    ms java.util.concurrent.TimeUnit/MILLISECONDS))
       ([promise ms _timeout-value]
        ;; Timeout value ignored — orTimeout always rejects with TimeoutException
        (.orTimeout ^java.util.concurrent.CompletableFuture promise
                    ms java.util.concurrent.TimeUnit/MILLISECONDS)))

     (defn resolved
       "Already-resolved promise."
       [v]
       (java.util.concurrent.CompletableFuture/completedFuture v))

     (defn rejected
       "Already-rejected promise."
       [e]
       (doto (java.util.concurrent.CompletableFuture.)
         (.completeExceptionally e)))))

;; ── JVM/CLJS backend (promesa passthrough) ─────────────────────────────
;; Guarded with :bb nil so bb skips the entire block.

#?(:bb nil
   :default
   (defn create
     "Create promise. f receives (resolve reject)."
     [f] (p/create f)))

#?(:bb nil
   :default
   (defn then
     "Chain success (and optional error) handler."
     ([promise f] (p/then promise f))
     ([promise f on-reject] (-> promise (p/then f) (p/catch on-reject)))))

#?(:bb nil
   :default
   (defn catch
     "Catch errors."
     [promise f] (p/catch promise f)))

#?(:bb nil
   :default
   (defn handle
     "Handle success or error — f receives (value error), one is nil."
     [promise f] (p/handle promise f)))

#?(:bb nil
   :default
   (defn all
     "All promises resolve → vector. Any rejects → rejection."
     [promises] (p/all promises)))

#?(:bb nil
   :default
   (defn timeout
     "Reject if not resolved within ms."
     ([promise ms] (p/timeout promise ms))
     ([promise ms timeout-value]
      (if (fn? timeout-value)
        (p/timeout promise ms timeout-value)
        (p/timeout promise ms (fn [] timeout-value))))))

#?(:bb nil
   :default
   (defn resolved
     "Already-resolved promise."
     [v] (p/resolved v)))

#?(:bb nil
   :default
   (defn rejected
     "Already-rejected promise."
     [e] (p/rejected e)))
