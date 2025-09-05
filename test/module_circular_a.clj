(ns module-circular-a
  (:require [system :as s]))

(s/defmanifest {:deps [:module-circular-b]})
