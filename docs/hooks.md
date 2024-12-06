## Hooks, Events and Slots

Module may define hook or register handler for existing hooks.
Events are one-way hooks - i.e. no response is expected (maybe async)
Slots are hook where only one hook may be registered




```clj
;; a
(defmodule
  {:description "..."
   :define
   {:hooks  {:authorize  {:args   [:system/context :http/operation :http/request]
                          :return [:http/auth-response]}}
    :events {:on-request {:args   [:system/context :http/request]}}
    :slots  {:dispatch {}}}}
  :register
  {:hooks
   {:http    {:authorize  {:fn #'authorize}}}
   :events   {:http    {:on-requet  {:fn #'on-request}}
              :pg.repo {:on-save    {:fn #'on-save}}}
   :slots    {:http    {:dispatch {}}}}))

;;b
(defmodule
  {:description "..."
   :config {}
   :hooks
   {::authorize  {:args   [:system/context :http/operation :http/request] :return [:http/auth-response]}
    ::on-request {:args   [:system/context :http/request]}}

   :handlers
   {:http/authorize    {:fn #'authorize }
    :http/on-request   {:fn #'on-request}
    :pg.repo/on-save   {:fn #'on-save   }}))

;; c
(defmodule
  {:description "..."
   :config {}
   :hooks
   [{:hook :authorize  :args [:system/context ::operation ::request] :return [::auth-response]}
    {:hook :on-request :args [:system/context ::request]}]

   :handlers
   [{:hook :authorize :module :http    :id ::authorize :fn #'authrize}
    {:hook :pg.repo   :module :on-save :id ::on-save   :fn #'on-save}]))

;; dynamic registration
(register-hook context
 {:module :http
  :hook   :authorize
  :id     ::authorize ;; can be deduced from namespace
  :fn     #'authorize
  :filter #'filter ;; can be done inside a hook
  })

```

There are system helpers to implement hooks

```clj

(system/get-hooks context {:module :http :hook :authorize})
(system/reduce-hooks context acc {:module :http :hook :authorize} (fn [acc hook]))
(system/chain-of-hooks context acc {:module :http :hook :authorize} (fn [acc hook]))
(system/call-slot context {:module :http :slot :slot} params)

```
