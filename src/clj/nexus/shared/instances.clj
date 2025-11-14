(ns nexus.shared.instances)

(defn closeable?
  [thing]
  (instance? java.io.Closeable thing))