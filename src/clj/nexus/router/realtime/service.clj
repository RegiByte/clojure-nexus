(ns nexus.router.realtime.service
  (:require
   [manifold.stream :as s]
   [integrant.core :as ig]
   [taoensso.telemere :as tel]))

;; =============================================================================
;; Stream Service - Manages SSE and WebSocket connections lifecycle
;; =============================================================================

(defn create-sse-stream!
  "Creates a new SSE stream and tracks it in the service atom.
   Returns {:stream-id id :stream stream}"
  [service]
  (let [stream-id (str (java.util.UUID/randomUUID))
        stream (s/stream)
        info {:type :sse
              :stream stream
              :created-at (System/currentTimeMillis)}]
    
    ;; Track the stream
    (swap! service assoc stream-id info)
    
    ;; Auto-cleanup on close
    (s/on-closed stream
                 (fn []
                   (swap! service dissoc stream-id)
                   (tel/log! :debug ["SSE stream auto-cleanup" {:stream-id stream-id}])))
    
    {:stream-id stream-id
     :stream stream}))

(defn create-ws-stream!
  "Creates a new WebSocket stream and tracks it in the service atom.
   Returns {:stream-id id :conn conn}"
  [service conn]
  (let [stream-id (str (java.util.UUID/randomUUID))
        info {:type :websocket
              :conn conn
              :created-at (System/currentTimeMillis)}]
    
    ;; Track the connection
    (swap! service assoc stream-id info)
    
    ;; Auto-cleanup on close
    (s/on-closed conn
                 (fn []
                   (swap! service dissoc stream-id)
                   (tel/log! :debug ["WebSocket stream auto-cleanup" {:stream-id stream-id}])))
    
    {:stream-id stream-id
     :conn conn}))

(defn close-stream!
  "Closes a specific stream by ID"
  [service stream-id]
  (when-let [info (get @service stream-id)]
    (tel/log! :debug ["Closing stream" {:stream-id stream-id :type (:type info)}])
    (let [closeable (or (:stream info) (:conn info))]
      (when-not (s/closed? closeable)
        (s/close! closeable)))
    (swap! service dissoc stream-id)
    true))

(defn close-all-streams!
  "Closes all tracked streams. Returns count of closed streams."
  [service]
  (let [stream-count (count @service)]
    (when (pos? stream-count)
      (tel/log! :info ["Closing all streams" {:count stream-count}])
      (doseq [[_stream-id info] @service]
        (let [closeable (or (:stream info) (:conn info))]
          (when-not (s/closed? closeable)
            (s/close! closeable))))
      (reset! service {}))
    stream-count))

(defn get-active-streams
  "Returns map of active stream IDs to their info"
  [service]
  @service)

(defn stream-count
  "Returns count of active streams"
  [service]
  (count @service))

;; =============================================================================
;; Integrant Lifecycle
;; =============================================================================

(defmethod ig/init-key ::service [_ _opts]
  (tel/log! :info "Initializing stream service")
  (atom {}))

(defmethod ig/halt-key! ::service [_ service]
  (tel/log! :info "Halting stream service")
  (close-all-streams! service))

