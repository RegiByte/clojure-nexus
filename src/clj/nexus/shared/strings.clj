(ns nexus.shared.strings)


(defn str->uuid
  [str]
  (try
    (java.util.UUID/fromString str)
    (catch Exception _e nil)))