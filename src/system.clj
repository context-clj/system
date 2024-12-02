(ns system
  (:require [system.config]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]))
;; TODO: rewrite start with context

(defn info [context event & [message opts]]
  (println event (or message "") (or opts "")))

(defn error [context event & [message opts]]
  (println event (or message "") (or opts "")))

(defn debug [context event & [message opts]]
  (println event (or message "") (or opts "")))

(s/def ::config :system.config/config-spec)
(s/def ::descripton string?)
(s/def ::manifest (s/keys :opt-un [::config ::description]))

(defmacro defmanifest [manifest]
  (when-not (s/valid? ::manifest manifest)
    (throw (ex-info "Invalid manifest" (s/explain-data ::manifest manifest))))
  (list 'def 'manifest manifest))

(defn- new-system [ & [config]]
  {:system (atom {:system/config (or config {})})})

(defn new-context [ctx & [params]]
  (merge (or params {}) {:system (:system ctx)}))

(defn -set-state [system key value]
  (swap! system assoc key value))

(defmacro set-state [system value]
  `(-set-state ~system ~(keyword (.getName *ns*)) ~value))

(defn -clear-state [system key]
  (swap! system dissoc key))

(defmacro clear-state [system]
  `(-clear-state ~system ~(keyword (.getName *ns*))))

(defn -get-state [system key]
  (get @system key))

(defmacro get-state [system]
  `(-get-state ~system ~(keyword (.getName *ns*))))

(defn -set-system-state [system key path value]
  (swap! system assoc-in (into [key] path) value))

(defmacro set-system-state [ctx path value]
  `(-set-system-state (:system ~ctx) ~(keyword (.getName *ns*)) ~path ~value))

(defn -clear-system-state [system key & [path]]
  (if (or (nil? path) (empty? path))
    (swap! system dissoc key)
    (swap! system (fn [x] (update-in x (into [key] (butlast path)) dissoc (last path))))))

(defmacro clear-system-state [ctx path]
  `(-clear-system-state (:system ~ctx) ~(keyword (.getName *ns*)) ~path))

(defn -update-system-state [system key path f]
  (swap! system update-in (into [key] path) f))

(defmacro update-system-state [ctx path f]
  `(-update-system-state (:system ~ctx) ~(keyword (.getName *ns*)) ~path ~f))

(defn -merge-system-state [system key path state]
  (swap! system update-in (into [key] path)
         (fn [st] (merge st state))))

(defmacro merge-system-state [ctx path state]
  `(-merge-system-state (:system ~ctx) ~(keyword (.getName *ns*)) ~path ~state))

(defn -get-system-state [system key path default]
  (get-in @system (into [key] path) default))

(defmacro get-system-state [ctx path & [default]]
  `(-get-system-state (:system ~ctx) ~(keyword (.getName *ns*)) ~path ~default))

(defmacro start-service [ctx & body]
  (let [key (.getName *ns*)]
    `(when-not (contains? (:services @(:system ~ctx)) '~key)
       (swap! (:system ~ctx) update :services (fn [x#] (conj (or x# #{}) '~key)))
       (let [state# (do ~@body)]
         (merge-system-state ~ctx [] state#)
         (println :start-module ~(name key))))))

(defmacro defstart [params & body]
  (assert (= 2 (count params)))
  (let [fn-name 'start]
    `(defn ~fn-name ~params
       (start-service ~(first params) ~@body))))

(defmacro stop-service [ctx & body]
  (let [key (.getName *ns*)]
    `(when (contains? (:services @(:system ~ctx)) '~key)
       ~@body
       (swap! (:system ~ctx) update :services (fn [x#] (when x# (disj x# '~key))))
       (clear-system-state ~ctx []))))

(defmacro defstop [params & body]
  (assert (= 2 (count params)))
  (let [fn-name 'stop]
    `(defn ~fn-name ~params
       (stop-service ~(first params) ~@body))))

(defn ctx-get [ctx path]
  (get-in ctx path))

(defn ctx-set [ctx path value]
  (assoc-in ctx path value))

(defn manifest-hook [ctx hook-name opts]
  (update-system-state ctx [:manifested-hooks hook-name] opts))

(defn register-hook [context hook-name hook-id hook]
  (assert hook-id)
  (info context ::register-hook (str hook-name " <- " hook-id))
  (set-system-state context [:registered-hooks hook-name hook-id] hook))

(defn get-hooks [context hook-name]
  (get-system-state context [:registered-hooks hook-name]))

(defn reduce-hooks [context hook-name acc f]
  (->> (get-hooks context hook-name)
       (reduce (fn [acc [k v]] (f acc k v)) acc)))

(defn reduce-hooks-into-vector
  [context hook-name & params]
  (reduce-hooks
   context hook-name []
   (fn [acc id {f :fn :as hook}]
     (system/debug context hook-name (str "with " id))
     (into acc (apply f context params)))))

(defn reduce-hooks-into-context
  [context hook-name & params]
  (reduce-hooks
   context hook-name context
   (fn [context id {f :fn :as hook}]
     (system/debug context hook-name (str "with " id))
     (apply f context params))))

(defn -register-config [ctx service-name config]
  (update-system-state ctx [:config service-name] config))

(defmacro register-config [ctx config]
  (let [key (keyword (.getName *ns*))]
    `(-register-config ~ctx ~key ~config)))

(defn register-hooks-from-manifest [context manifest]
  (doseq [[hook-name hooks] (:register-hook manifest)]
    (doseq [[hook-id hook] hooks]
      (register-hook context hook-name hook-id hook))))

(defn configs-from-manifest [context manifest svs config]
  (when-let [schema (get-in manifest [:config])]
    (info context ::validate svs)
    (let [module-key     (keyword svs)
          module-config  (get config module-key)
          coerced-config (system.config/coerce schema module-config)
          errors         (system.config/validate schema coerced-config)]
      (if (seq errors)
        (do (error context ::invalid-config (str svs ": " (str/join ", " errors)) )
            (set-system-state context [:errors module-key] errors))
        (do (info context ::valid-config svs)
            (set-system-state context [:configs module-key] coerced-config))))))

(defn register-slot [context slot-name slot]
  (debug context ::register-slot (str slot-name " <- " slot))
  (set-system-state context [:registered-slot slot-name] slot))

(defn register-slots-from-manifest [context manifest]
  (doseq [[slot-name slot] (:register-slot manifest)]
    (register-slot context slot-name slot)))

(defn call-slot [context slot-name & params]
  (if-let [slot (get-system-state context [:registered-slot slot-name])]
    (apply (:fn slot) context params)
    (throw (Exception. (str "No slot registered for " slot-name)))))

(defn get-hooks [context hook-name]
  (get-system-state context [:registered-hooks hook-name]))

(defn read-manifests [context {services :services :as config}]
  (doseq [svs services]
    (require (symbol svs))
    (info context ::load svs)
    (if-let [manifest (resolve (symbol (name svs) "manifest"))]
      (let [manifest (var-get manifest)]
        (info context ::manifest svs)
        (set-system-state context [:manifests (keyword svs)] manifest)
        (register-hooks-from-manifest context manifest)
        (register-slots-from-manifest context manifest)
        (configs-from-manifest context manifest svs config))
      (throw (Exception. (str "No module " svs))))))

(defn start-services [context {services :services :as config}]
  (doseq [svs services]
    (when-let [start-fn (resolve (symbol (name svs) "start"))]
      (let [module-config (get-system-state context [:configs (keyword svs)])]
        (start-fn context module-config)))))

(defn start-system
  "config {:services [\"svs1\", \"svs2\"] :svs1 {} :svs2 {}}"
  [{_services :services :as config}]
  (let [context (new-system {})]
    (read-manifests context config)
    (let [errors (get-system-state context [:errors])]
      (when (seq errors)
        (error context ::config-error (str "Can't start, invalid configs: " (pr-str errors)))
        (throw (Exception. "Invalid config"))))
    (start-services context config)
    context))

(defn stop-system [ctx]
  (let [system @(:system ctx)]
    (doseq [sv (:services system)]
      (require [sv])
      (when-let [stop-fn (resolve (symbol (name sv) "stop"))]
        (info ctx :stoping sv)
        (stop-fn ctx (get system (keyword (name sv))))
        (info ctx :stopped sv)))))


;; TODO: think about name convention like module-<module-name>.clj
;; TODO: pass service state to stop
;; TODO: rename service into module - more generic
;; TODO: make register module using manifest
;; TODO: open telemetry out of the box
;; on module registration it register all config params
;; this params are used to validate before start

