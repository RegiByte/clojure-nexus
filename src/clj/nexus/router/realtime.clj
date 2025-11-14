(ns nexus.router.realtime
  (:require
   [aleph.http :as http]
   [jsonista.core :as json]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [nexus.router.realtime.service :as stream-svc]
   [taoensso.telemere :as tel]))

;; =============================================================================
;; Server-Sent Events (SSE) Handlers
;; =============================================================================

(defn format-sse-event
  "Format data as SSE event"
  [data]
  (str "data: " (json/write-value-as-string data) "\n\n"))

(defn put-text!
  "Asynchronously push a text payload to a stream, logging failures."
  ([stream text log-context]
   (d/on-realized
    (s/put! stream text)
    (constantly nil)
    (fn [err]
      (tel/log! :warn ["Failed to deliver realtime payload"
                       (assoc log-context :error err)]))))
  ([stream text]
   (put-text! stream text {})))

(defn put-json!
  "Serialize payload to JSON and put onto stream asynchronously."
  ([stream payload log-context]
   (put-text! stream (json/write-value-as-string payload) log-context))
  ([stream payload]
   (put-json! stream payload {})))

(defn sse-handler
  "Creates an SSE endpoint that sends periodic updates to clients.
   
   Uses stream service for lifecycle management.
   Sends a counter that increments every 2 seconds."
  [request]
  (tel/log! :info "SSE connection established")
  (let [stream-service (get-in request [:context :stream-service])
        {:keys [stream-id stream]} (stream-svc/create-sse-stream! stream-service)
        counter (atom -1)
        periodic-stream (s/periodically
                         2000
                         (fn []
                           (format-sse-event
                            {:counter (swap! counter inc)
                             :timestamp (System/currentTimeMillis)})))]

    ;; Send initial messages asynchronously (don't block the handler)
    (d/chain (s/put! stream "event: connected\n")
             (fn [_]
               (s/put! stream (format-sse-event {:message "Connected to SSE stream"
                                                 :stream-id stream-id})))
             (fn [_]
               ;; Connect periodic stream after initial messages
               (s/connect periodic-stream stream)))

    ;; Set up cleanup - close the periodic stream when connection closes
    (s/on-closed stream
                 (fn []
                   (s/close! periodic-stream)
                   (tel/log! :info ["SSE connection closed" {:stream-id stream-id}])))

    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection" "keep-alive"
               "X-Accel-Buffering" "no"}
     :body stream}))

(defn sse-multi-event-handler
  "SSE endpoint with multiple event types.
   
   Uses stream service for lifecycle management.
   Alternates between price-update and notification events every 3 seconds."
  [request]
  (tel/log! :info "SSE multi-event connection established")
  (let [stream-service (get-in request [:context :stream-service])
        {:keys [stream-id stream]} (stream-svc/create-sse-stream! stream-service)
        counter (atom 0)
        periodic-stream (s/periodically 3000
                                        (fn []
                                          (let [i @counter
                                                _ (swap! counter inc)]
                                            (if (even? i)
                                              ;; Price update event
                                              (str "event: price-update\n"
                                                   "data: " (json/write-value-as-string
                                                             {:price (+ 100 (rand-int 50))
                                                              :timestamp (System/currentTimeMillis)})
                                                   "\n\n")
                                              ;; Notification event
                                              (str "event: notification\n"
                                                   "data: " (json/write-value-as-string
                                                             {:message (str "Update " i)
                                                              :timestamp (System/currentTimeMillis)})
                                                   "\n\n")))))]

    ;; Send initial messages asynchronously (don't block the handler)
    (d/chain (s/put! stream "event: connected\n")
             (fn [_]
               (s/put! stream (format-sse-event {:message "Connected to multi-event stream"
                                                 :stream-id stream-id})))
             (fn [_]
               ;; Connect periodic stream after initial messages
               (s/connect periodic-stream stream)))

    ;; Set up cleanup - close the periodic stream when connection closes
    (s/on-closed stream
                 (fn []
                   (s/close! periodic-stream)
                   (tel/log! :info ["SSE multi-event connection closed" {:stream-id stream-id}])))

    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection" "keep-alive"
               "X-Accel-Buffering" "no"}
     :body stream}))

;; =============================================================================
;; WebSocket Handlers
;; =============================================================================

(defn websocket-handler
  "Simple WebSocket echo endpoint.
   
   Uses stream service for lifecycle management.
   Sends a welcome message, then echoes back any received messages."
  [request]
  (let [stream-service (get-in request [:context :stream-service])
        conn @(http/websocket-connection request)
        {:keys [stream-id]} (stream-svc/create-ws-stream! stream-service conn :echo-channel)
        send! (fn [payload]
                (put-json! conn payload {:stream-id stream-id :handler :echo}))]
    (tel/log! :info ["WebSocket echo connection established" {:stream-id stream-id}])

    ;; Send welcome message
    (send! {:type "welcome"
            :message "Welcome to WebSocket!"
            :stream-id stream-id})

    ;; Echo messages without blocking dedicated threads
    (s/consume
     (fn [msg]
       (when (some? msg)
         (try
           (tel/log! :debug ["WebSocket received" {:message msg :stream-id stream-id}])
           (send! {:type "echo" :message msg})
           (catch Exception e
             (tel/log! :error ["WebSocket error" {:stream-id stream-id :error e}])
             (s/close! conn)))))
     conn)

    (s/on-closed conn
                 (fn []
                   (tel/log! :info ["WebSocket echo connection closed" {:stream-id stream-id}])))

    nil))

(defn broadcast-handler
  "WebSocket endpoint that broadcasts messages to all connected clients.
   
   Uses stream service for lifecycle management.
   Simple chat room implementation - messages sent to one client are broadcast to all."
  [request]
  (let [stream-service (get-in request [:context :stream-service])
        conn @(http/websocket-connection request)
        {:keys [stream-id]} (stream-svc/create-ws-stream! stream-service conn :broadcast-channel)
        send! (fn [payload]
                (put-json! conn payload {:stream-id stream-id :handler :broadcast}))
        ;; Helper to broadcast to all connected WebSocket clients
        broadcast-to-all! (fn [message-data]
                            (let [json-msg (json/write-value-as-string message-data)]
                              (doseq [[client-id info] (stream-svc/get-active-streams-by-channel
                                                        stream-service
                                                        :broadcast-channel)]
                                (let [{:keys [type conn]} info
                                      ws-conn conn]
                                  (when (and (= :websocket type)
                                             (not (s/closed? ws-conn)))
                                    (put-text! ws-conn json-msg {:stream-id client-id
                                                                 :handler :broadcast}))))))]

    (tel/log! :info ["WebSocket broadcast connection established" {:stream-id stream-id}])

    ;; Send welcome message to this client
    (send! {:type "system"
            :message "Connected to broadcast channel"
            :stream-id stream-id})

    ;; Notify all clients about new connection
    (let [client-count (stream-svc/count-streams
                        (stream-svc/get-active-streams-by-channel
                         stream-service
                         :broadcast-channel))]
      (tel/log! :info ["User joined" {:client-count client-count :stream-id stream-id}])
      (broadcast-to-all! {:type "system" :message (str "User joined. Total clients: " client-count)}))

    ;; Listen for messages without dedicating a thread per connection
    (s/consume
     (fn [msg]
       (when (some? msg)
         (try
           (tel/log! :debug ["Broadcasting" {:message msg :stream-id stream-id}])
           (broadcast-to-all! {:type "broadcast" :message msg})
           (catch Exception e
             (tel/log! :error ["Broadcast error" {:error e :stream-id stream-id}])
             (s/close! conn)))))
     conn)

    (s/on-closed conn
                 (fn []
                   (let [client-count (stream-svc/count-streams
                                       (stream-svc/get-active-streams-by-channel
                                        stream-service
                                        :broadcast-channel))]
                     (tel/log! :info ["User left" {:client-count client-count :stream-id stream-id}])
                     (broadcast-to-all! {:type "system"
                                         :message (str "User left. Total clients: " client-count)}))))

    nil))

;; =============================================================================
;; Routes
;; =============================================================================

(defn sse-routes []
  ["/sse"
   {:tags #{"realtime"}}

   ["/events"
    {:get {:summary "Server-Sent Events stream"
           :description "Opens an SSE connection for real-time updates"
           :handler sse-handler}}]

   ["/multi-events"
    {:get {:summary "SSE with multiple event types"
           :description "Opens an SSE connection with different event types"
           :handler sse-multi-event-handler}}]])

(defn websocket-routes []
  ["/ws"
   {:tags #{"realtime"}}

   ["/echo"
    {:get {:summary "WebSocket echo endpoint"
           :description "Opens a WebSocket connection that echoes messages back"
           :handler websocket-handler}}]

   ["/broadcast"
    {:get {:summary "WebSocket broadcast endpoint"
           :description "Opens a WebSocket connection where messages are broadcast to all clients"
           :handler broadcast-handler}}]])

(defn routes
  "Main routes for realtime endpoints"
  []
  [(sse-routes)
   (websocket-routes)])
