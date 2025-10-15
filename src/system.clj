(ns system
  (:require
   [clojure.pprint :as pp]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [system.cli :as cli]
   [system.config]
   [system.manifest :refer [find-manifest-var load-deps unload-deps]]
   [system.meta :refer [find-var-with-meta]]))
;; TODO: rewrite start with context


(def ^:const log-levels {:off -1 :error 0 :info 1 :debug 2})
(def ^:const log-levels-inv (reduce (fn [acc [k v]] (assoc acc v k)) {} log-levels))

(defn ctx-set-log-level [context level]
  (assert (contains? log-levels level))
  (assoc context ::log-level (get log-levels level)))

(defn- ctx-get-log-level-n [context]
  (or (-> context ::log-level)
      (some-> context :system deref :system/config :system/log-level)
      1))

(defn ctx-get-log-level [context]
  (log-levels-inv (ctx-get-log-level-n context)))

(defn error [context event & [message opts]]
  (let [lvl (ctx-get-log-level-n context)]
    (when (or (nil? lvl) (>= lvl (log-levels :error)))
      (println :error event (or message "") (or opts "")))))

(defn info [context event & [message opts]]
  (let [lvl (ctx-get-log-level-n context)]
    (when (or (nil? lvl) (>= lvl (log-levels :info)))
      (println :info event (or message "") (or opts "")))))

(defn debug [context event & [message opts]]
  (let [lvl (ctx-get-log-level-n context)]
    (when (or (nil? lvl) (>= lvl (log-levels :debug)))
      (println :debug event (or message "") (or opts "")))))

(s/def ::config :system.config/config-spec)
(s/def ::description string?)
(s/def ::manifest (s/keys :opt-un [::config ::description]))

(defmacro defmanifest [manifest]
  `(do
     (let [context-clj-ns-helper-sym# (gensym "context-clj-ns-helper")]
       (def context-clj-ns-helper-sym# nil)
       (let [context-clj-ns# (-> context-clj-ns-helper-sym# var meta :ns)]
         (when-let [manifest-var# (find-manifest-var context-clj-ns#)]
           (when-let [deps# (-> manifest-var# var-get :deps seq)]
             (unload-deps deps#))
           (ns-unmap context-clj-ns# (-> manifest-var# meta :name)))

         (let [result# (s/conform ~::manifest ~manifest)]
           (if (= :clojure.spec.alpha/invalid result#)
             (throw (ex-info "Invalid manifest"
                             (s/explain-data ~::manifest ~manifest)))
             (let [manifest-var# (intern context-clj-ns#
                                         (gensym "context-clj-manifest-")
                                         (with-meta result# {:context-clj/manifest true}))]
               (when-let [deps# (-> result# :deps seq)]
                 (load-deps deps#))
               manifest-var#)))))))

(defn- new-system [& [config]]
  {:system (atom {:system/config (or config {})})
   :cache (atom {})})

(defn new-context [ctx & [params]]
  (merge (or params {}) {:system (:system ctx) :cache (atom {})}))

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


(defn -get-context-cache [{cache :cache :as ctx} key path update-fn]
  (let [v-path (into [key] path)]
    (if-let [v (get-in @cache v-path)]
      v
      (when-let [v (update-fn)]
        (swap! cache assoc-in v-path v)
        v))))

(defmacro get-context-cache [ctx path update-fn]
  `(-get-context-cache ~ctx ~(keyword (.getName *ns*)) ~path ~update-fn))

(defn -clear-context-cache [{cache :cache :as ctx} key path]
  (let [v-path (into [key] path)]
    (swap! cache assoc-in v-path nil)))

(defmacro clear-context-cache [ctx path]
  `(-clear-context-cache ~ctx ~(keyword (.getName *ns*)) ~path))

(defn -get-config [system module-key config-key default]
  (get-in @system [:system :configs module-key config-key] default))

(defmacro get-config [ctx config-key & [default]]
  `(-get-config (:system ~ctx) ~(keyword (.getName *ns*)) ~config-key ~default))

(defmacro start-service [ctx & body]
  (let [key (.getName *ns*)]
    `(when-not (contains? (:services @(:system ~ctx)) '~key)
       (swap! (:system ~ctx) update :services (fn [x#] (conj (or x# #{}) '~key)))
       (let [state# (do ~@body)]
         (when (map? state#) (merge-system-state ~ctx [] state#))
         (info ~ctx ::start-module ~(name key))))))

(defmacro defstart [[ctx cfg] & body]
  `(intern *ns*
           (symbol "start")
           (with-meta
             (fn [~ctx ~cfg]
               (let [b# (do ~@body)]
                 (if-not (or (map? b#) (nil? b#))
                   (throw
                    (ex-info (str "start body should return config map, but got " (type b#))
                             {:return b#}))
                   (system/start-service ~ctx b#))))
             {:context-clj/defstart true})))

(defmacro stop-service [ctx & body]
  (let [key (.getName *ns*)]
    `(when (contains? (:services @(:system ~ctx)) '~key)
       ~@body
       (swap! (:system ~ctx) update :services (fn [x#] (when x# (disj x# '~key))))
       (clear-system-state ~ctx []))))

(defmacro defstop [[ctx state] & body]
  `(intern *ns*
           (symbol "stop")
           (with-meta
             (fn [~ctx ~state]
               (stop-service ~ctx ~@body))
             {:context-clj/defstop true})))

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
        (do (error context ::invalid-config (str svs ": " (str/join ", " errors)))
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
    (require (symbol svs) :reload)
    (info context ::load svs)
    (if-let [manifest (some-> svs name symbol find-ns find-manifest-var var-get)]
      (do
        (info context ::manifest svs)
        (set-system-state context [:manifests (keyword svs)] manifest)
        (register-hooks-from-manifest context manifest)
        (register-slots-from-manifest context manifest)
        (configs-from-manifest context manifest svs config))
      (throw (Exception. (str "No module " svs))))))

(defn- find-stop-fn-var [ns]
  (find-var-with-meta ns :context-clj/defstop))

(defn stop-system [ctx]
  (let [system @(:system ctx)]
    (doseq [sv (:services system)]
      (require [sv])
      (let [sv-ns (-> sv name symbol find-ns)]
        (when-let [stop-fn (some-> sv-ns find-stop-fn-var var-get)]
          (binding [*ns* sv-ns]
            (info ctx :stoping sv)
            (stop-fn ctx (get system (keyword (name sv))))
            (info ctx :stopped sv)))))))

(defn- find-start-fn-var [ns]
  (find-var-with-meta ns :context-clj/defstart))

(defn start-services [context {services :services :as _config}]
  (try
    (doseq [svs services]
      (let [svs-ns (-> svs name symbol find-ns)]
        (if-let [start-fn (some-> svs-ns find-start-fn-var var-get)]
          (let [module-config (get-system-state context [:configs (keyword svs)])]
            (binding [*ns* svs-ns]
              (start-fn context module-config)))
          (swap! (:system context) update :services (fn [x#] (conj (or x# #{}) (symbol svs)))))))
    (catch Throwable t
      (stop-system context)
      (throw (.fillInStackTrace t)))))

(defn start-system
  "config {:services [\"svs1\", \"svs2\"] :svs1 {} :svs2 {}}"
  [{_services :services :as config}]
  (let [context (new-system config)]
    (read-manifests context config)
    (let [errors (get-system-state context [:errors])]
      (when (seq errors)
        (error context ::config-error (str "Can't start, invalid configs: " (pr-str errors)))
        (throw (Exception. "Invalid config"))))
    (try (start-services context config)
         (catch Exception e
           (try (stop-system context) (catch Exception e))
           (throw e)))
    context))

(defn- usage [options-summary]
  (->> ["Default context-clj system runner"
        ""
        "Usage:"
        "  clj -M -i <path-to-main-module.clj> -m system"
        "      --modules '[\"<module-1>\" \"<module-2>\"]'"
        "      --module-1.param-1 <param-1>"
        "      --module-2.param-2 <param-2>"
        ""
        "Options:"
        options-summary
        ""
        "Please refer to context-clj README to override the default runner:"
        "https://github.com/context-clj/system/blob/main/README.md"]
       (str/join \newline)))

(defn -main [& args]
  (let [{:keys [options errors summary]}
        (cli/parse-args args)]
    (cond
      (:help options)
      (cli/exit 0 (usage summary))

      errors
      (cli/exit 1 (cli/error-msg errors))

      :else
      (let [system-config (cli/options->system-config options)]
        (println "\nStarting system with config:")
        (pp/pprint system-config)
        (println)
        (start-system system-config)))))

;; helper macro for tests
(defmacro ensure-context [cfg]
  `(do
     (defonce ~'context nil)
     (defonce ~'context-atom (atom nil))
     (defn ~'reload-context []
       (system/stop-system ~'context)
       (reset! ~'context-atom nil)
       (def ~'context (system/start-system ~cfg))
       (reset! ~'context-atom ~'context))
     (defn ~'ensure-context []
       (when-not @~'context-atom
         (def ~'context (system/start-system ~cfg))
         (reset! ~'context-atom ~'context)))))

(def cfg {:host "localhost" :port  5401 :database "context_pg" :user "admin" :password "admin"})

;; TODO: add context cache set-context-cache, update-context-cache, get-context-cache and clear-context-cache
;; TODO: think about name convention like module-<module-name>.clj
;; TODO: pass service state to stop
;; TODO: rename service into module - more generic
;; TODO: make register module using manifest
;; TODO: open telemetry out of the box
;; on module registration it register all config params
;; this params are used to validate before start
