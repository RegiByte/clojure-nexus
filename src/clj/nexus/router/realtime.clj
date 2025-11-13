(ns nexus.router.realtime
  (:require
   [aleph.http :as http]
   [jsonista.core :as json]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [taoensso.telemere :as tel]))

;; =============================================================================
;; Server-Sent Events (SSE) Handlers
;; =============================================================================

(defn sse-handler
  "Creates an SSE endpoint that sends periodic updates to clients.
   
   SSE is great for:
   - Real-time notifications
   - Live data feeds
   - Progress updates
   - One-way server-to-client communication"
  [_request]
  (let [stream (s/stream)]
    ;; Start sending events in the background
    (future
      (try
        (tel/log! :info "SSE connection established")
        ;; Send initial connection message
        @(s/put! stream "event: connected\n")
        @(s/put! stream (str "data: " (json/write-value-as-string {:message "Connected to SSE stream"}) "\n\n"))
        
        ;; Send periodic updates every 2 seconds
        (loop [counter 0]
          (when-not (s/closed? stream)
            ;; Wait 2 seconds
            (Thread/sleep 2000)
            
            ;; Send event with data
            (let [event-data (str "data: " 
                                (json/write-value-as-string 
                                  {:counter counter 
                                   :timestamp (System/currentTimeMillis)})
                                "\n\n")]
              (when @(s/put! stream event-data)
                (recur (inc counter))))))
        
        (catch Exception e
          (tel/log! :error ["SSE Error" {:error e}]))
        (finally
          (tel/log! :info "SSE connection closed")
          (s/close! stream))))
    
    ;; Set up SSE response headers
    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection" "keep-alive"
               "X-Accel-Buffering" "no"}
     :body stream}))

(defn sse-multi-event-handler
  "SSE endpoint with multiple event types.
   
   Clients can listen to specific event types:
   eventSource.addEventListener('price-update', handler)
   eventSource.addEventListener('notification', handler)"
  [_request]
  (let [stream (s/stream)]
    (future
      (try
        (tel/log! :info "SSE multi-event connection established")
        ;; Send initial connection
        @(s/put! stream "event: connected\n")
        @(s/put! stream (str "data: " (json/write-value-as-string {:message "Connected to multi-event stream"}) "\n\n"))
        
        ;; Send different types of events
        (loop [i 0]
          (when-not (s/closed? stream)
            (Thread/sleep 3000)
            
            ;; Alternate between event types
            (if (even? i)
              ;; Price update event
              (do
                @(s/put! stream "event: price-update\n")
                @(s/put! stream (str "data: " 
                                   (json/write-value-as-string 
                                     {:price (+ 100 (rand-int 50))
                                      :timestamp (System/currentTimeMillis)})
                                   "\n\n")))
              ;; Notification event
              (do
                @(s/put! stream "event: notification\n")
                @(s/put! stream (str "data: " 
                                   (json/write-value-as-string 
                                     {:message (str "Update " i)
                                      :timestamp (System/currentTimeMillis)})
                                   "\n\n"))))
            
            (recur (inc i))))
        
        (catch Exception e
          (tel/log! :error ["SSE multi-event error" {:error e}]))
        (finally
          (tel/log! :info "SSE multi-event connection closed")
          (s/close! stream))))
    
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
  "Creates a WebSocket endpoint for bidirectional communication.
   
   WebSockets are great for:
   - Chat applications
   - Real-time collaboration
   - Gaming
   - Two-way communication with low latency"
  [request]
  @(d/chain
    (http/websocket-connection request)
    (fn [conn]
      (future
        (try
          (tel/log! :info "WebSocket echo connection established")
          ;; Send welcome message
          @(s/put! conn (json/write-value-as-string {:type "welcome" :message "Welcome to WebSocket!"}))
          
          ;; Echo back any messages received
          (loop []
            (when-let [msg @(s/take! conn)]
              (tel/log! :debug ["WebSocket received message" {:message msg}])
              
              ;; Echo the message back with a prefix
              @(s/put! conn (json/write-value-as-string {:type "echo" :message msg}))
              
              (recur)))
          
          (catch Exception e
            (tel/log! :error ["WebSocket error" {:error e}]))
          (finally
            (tel/log! :info "WebSocket echo connection closed")
            (s/close! conn))))
      nil)))

;; WebSocket with broadcast to all connected clients
(def connected-clients (atom #{}))

(defn broadcast-handler
  "WebSocket endpoint that broadcasts messages to all connected clients.
   
   Use case: Chat room, live notifications to all users"
  [request]
  @(d/chain
    (http/websocket-connection request)
    (fn [conn]
      ;; Add this client to the set
      (swap! connected-clients conj conn)
      
      (future
        (try
          (tel/log! :info "WebSocket broadcast connection established")
          @(s/put! conn (json/write-value-as-string {:type "system" :message "Connected to broadcast channel"}))
          
          ;; Notify all clients about new connection
          (let [client-count (count @connected-clients)]
            (tel/log! :info ["User joined broadcast" {:client-count client-count}])
            (doseq [client @connected-clients]
              (when-not (s/closed? client)
                @(s/put! client (json/write-value-as-string 
                                 {:type "system" 
                                  :message (str "User joined. Total clients: " client-count)})))))
          
          ;; Listen for messages from this client
          (loop []
            (when-let [msg @(s/take! conn)]
              (tel/log! :debug ["Broadcasting message" {:message msg}])
              
              ;; Send to all connected clients
              (doseq [client @connected-clients]
                (when-not (s/closed? client)
                  @(s/put! client (json/write-value-as-string {:type "broadcast" :message msg}))))
              
              (recur)))
          
          (catch Exception e
            (tel/log! :error ["Broadcast error" {:error e}]))
          (finally
            ;; Remove client when disconnected
            (swap! connected-clients disj conn)
            (let [client-count (count @connected-clients)]
              (tel/log! :info ["User left broadcast" {:client-count client-count}])
              (doseq [client @connected-clients]
                (when-not (s/closed? client)
                  @(s/put! client (json/write-value-as-string 
                                   {:type "system" 
                                    :message (str "User left. Total clients: " client-count)})))))
            (s/close! conn))))
      nil)))

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
