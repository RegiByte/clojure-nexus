(ns nexus.router.realtime
  (:require
   [aleph.http :as http]
   [jsonista.core :as json]
   [manifold.stream :as s]
   [taoensso.telemere :as tel]))

;; =============================================================================
;; Server-Sent Events (SSE) Handlers
;; =============================================================================

(defn format-sse-event
  "Format data as SSE event"
  [data]
  (str "data: " (json/write-value-as-string data) "\n\n"))

(defn sse-handler
  "Creates an SSE endpoint that sends periodic updates to clients.
   
   Simple implementation using manifold.stream/periodically.
   Sends a counter that increments every 2 seconds."
  [_request]
  (tel/log! :info "SSE connection established")
  (let [counter (atom -1)
        stream (s/stream)
        periodic-stream (s/periodically 2000
                                        (fn []
                                          (println "stream closed?" (s/closed? stream))
                                          (format-sse-event
                                           {:counter (swap! counter inc)
                                            :timestamp (System/currentTimeMillis)})))]
    
    ;; Send initial messages asynchronously (don't block the handler)
    (future
      (s/put! stream "event: connected\n")
      (s/put! stream (format-sse-event {:message "Connected to SSE stream"}))
      ;; Connect periodic stream after initial messages
      (s/connect periodic-stream stream))
    
    ;; Set up cleanup - close the periodic stream when connection closes
    (s/on-closed stream
                 (fn []
                   (s/close! periodic-stream)
                   (tel/log! :info "SSE connection closed")))
    
    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection" "keep-alive"
               "X-Accel-Buffering" "no"}
     :body stream}))

(defn sse-multi-event-handler
  "SSE endpoint with multiple event types.
   
   Alternates between price-update and notification events every 3 seconds."
  [_request]
  (tel/log! :info "SSE multi-event connection established")
  (let [counter (atom 0)
        stream (s/stream)
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
    (future
      (s/put! stream "event: connected\n")
      (s/put! stream (format-sse-event {:message "Connected to multi-event stream"}))
      ;; Connect periodic stream after initial messages
      (s/connect periodic-stream stream))
    
    ;; Set up cleanup - close the periodic stream when connection closes
    (s/on-closed stream
                 (fn []
                   (s/close! periodic-stream)
                   (tel/log! :info "SSE multi-event connection closed")))
    
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
   Sends a welcome message, then echoes back any received messages."
  [request]
  (let [conn @(http/websocket-connection request)]
    (tel/log! :info "WebSocket echo connection established")
    
    ;; Send welcome message
    @(s/put! conn (json/write-value-as-string {:type "welcome" :message "Welcome to WebSocket!"}))
    
    ;; Echo messages in a background thread
    (future
      (try
        (loop []
          (when-let [msg @(s/take! conn)]
            (tel/log! :debug ["WebSocket received" {:message msg}])
            @(s/put! conn (json/write-value-as-string {:type "echo" :message msg}))
            (recur)))
        (catch Exception e
          (tel/log! :error ["WebSocket error" {:error e}]))
        (finally
          (tel/log! :info "WebSocket echo connection closed")
          (s/close! conn))))
    
    nil))

;; WebSocket with broadcast to all connected clients
(def connected-clients (atom #{}))

(defn broadcast-to-all!
  "Send a message to all connected clients"
  [message-data]
  (let [json-msg (json/write-value-as-string message-data)]
    (doseq [client @connected-clients]
      (when-not (s/closed? client)
        @(s/put! client json-msg)))))

(defn broadcast-handler
  "WebSocket endpoint that broadcasts messages to all connected clients.
   Simple chat room implementation."
  [request]
  (let [conn @(http/websocket-connection request)]
    (tel/log! :info "WebSocket broadcast connection established")
    
    ;; Add this client to the set
    (swap! connected-clients conj conn)
    
    ;; Send welcome message to this client
    @(s/put! conn (json/write-value-as-string {:type "system" :message "Connected to broadcast channel"}))
    
    ;; Notify all clients about new connection
    (let [client-count (count @connected-clients)]
      (tel/log! :info ["User joined" {:client-count client-count}])
      (broadcast-to-all! {:type "system" :message (str "User joined. Total clients: " client-count)}))
    
    ;; Listen for messages in background thread
    (future
      (try
        (loop []
          (when-let [msg @(s/take! conn)]
            (tel/log! :debug ["Broadcasting" {:message msg}])
            (broadcast-to-all! {:type "broadcast" :message msg})
            (recur)))
        (catch Exception e
          (tel/log! :error ["Broadcast error" {:error e}]))
        (finally
          ;; Clean up when client disconnects
          (swap! connected-clients disj conn)
          (let [client-count (count @connected-clients)]
            (tel/log! :info ["User left" {:client-count client-count}])
            (broadcast-to-all! {:type "system" :message (str "User left. Total clients: " client-count)}))
          (s/close! conn))))
    
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
