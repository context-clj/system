(ns module-circular-b
  (:require [system :as s]))

(s/defmanifest {:deps [:module-circular-a]})
