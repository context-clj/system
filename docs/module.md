# Module

Module is a basic unit of the system and just a clojure namespace

Module declare

* deps
* config params
* define hooks, events and slots
* register hooks, event and slot handlers

## Manifest

```clj

(system/defmanifest {
 :description "..."
 :deps ["mod1" "mod2"]
 :config {:enable      {:type "text" :required true :default "ok"}
          :other-param {:type "text" :required true :default "ok"}}
 :define   {:hooks {} :events {} :slot {}}
 :register {:hooks {} :events {} :slot {}}})

```

## Configuration



## Start and stop

May have a state and start/stop functions

```clj
(defstart [context config]


)


(defstop [context state]

)
```

## Hooks, Events and Slots

Module may define hooks for extensibility

Module may hook into existing hooks to extend functionality

Slot hook is a special hook case, where only one hook can be registered.
Using slots module may declare interface, but delegate implementation to other module

Module may publish and subscribe to events

## Service functions


## Helpers

TODO: should we introduce link or configure function?

There are set of helper funcitons

* set-system-state
* get-system-state
* get-conifg
* set-config
* get-hooks
* publish event





(ns repo)

(defmanifest
 {:define {:slot {:save {:args [] :return []}}}})

(defn save [context params]
  ;; impl
  (validate params)
  (system/call-slot context :save params))

----

(ns client-module
 (:require [repo-interface :as i]))

(defn create [context params]
  (i/save context {....}))

----

(ns repo-impl)

(defmanifest
  {:register {:slot {:save {:fn '#save}}}})

(defn save [context params]
  (pg/create ))
