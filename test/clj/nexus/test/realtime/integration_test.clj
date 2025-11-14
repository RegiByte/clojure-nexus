(ns nexus.test.realtime.integration-test
  (:require
   [aleph.http :as http]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [clojure.test :as t]
   [jsonista.core :as json]
   [manifold.stream :as s]
   [nexus.shared.instances :refer [closeable?]]
   [nexus.test.helpers :as th]
   [nexus.test.test-system :as test-sys])
  (:import
   [java.io BufferedReader InputStreamReader]))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn parse-sse-event
  "Parse SSE event format into a map.
   
   SSE format:
   event: event-name
   data: {json data}
   
   Or just:
   data: {json data}"
  [event-string]
  (let [lines (str/split-lines event-string)
        event-type (some->> lines
                            (filter #(str/starts-with? % "event:"))
                            first
                            (re-find #"event:\s*(.+)")
                            second
                            str/trim)
        data (some->> lines
                      (filter #(str/starts-with? % "data:"))
                      first
                      (re-find #"data:\s*(.+)")
                      second
                      str/trim
                      json/read-value)]
    {:event event-type
     :data data}))

(defn collect-ws-messages
  "Collect n messages from a WebSocket connection with timeout"
  [ws-url n timeout-ms]
  (let [conn @(http/websocket-client ws-url)
        messages (atom [])]
    (try
      (dotimes [_ n]
        (when-let [msg @(s/try-take! conn (long timeout-ms))]
          (swap! messages conj (json/read-value msg))))
      @messages
      (finally
        (s/close! conn)))))

(defn collect-sse-events
  "Collect n SSE events with timeout"
  [sse-url n timeout-ms]
  (let [response @(http/get sse-url {:as :stream})
        body (:body response)
        reader (BufferedReader. (InputStreamReader. body))
        events (atom [])
        start-time (System/currentTimeMillis)]
    (try
      (loop [collected 0]
        (when (< collected n)
          (let [elapsed (- (System/currentTimeMillis) start-time)]
            (when (< elapsed timeout-ms)
              ;; Read lines until we get a complete event (ends with blank line)
              (let [event-lines (loop [lines []]
                                  (if-let [line (.readLine reader)]
                                    (if (str/blank? line)
                                      lines  ; End of event
                                      (recur (conj lines line)))
                                    lines))]
                (when (seq event-lines)
                  (let [event-str (str/join "\n" event-lines)]
                    (swap! events conj (parse-sse-event event-str))
                    (recur (inc collected)))))))))
      @events
      (finally
        (.close reader)))))

;; ;; =============================================================================
;; ;; WebSocket Tests
;; ;; =============================================================================

(deftest websocket-echo-test
  (testing "WebSocket echo endpoint receives and echoes messages"
    (test-sys/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              host (th/server->ws-host server)
              conn @(http/websocket-client (str host "/api/realtime/ws/echo"))]

          (try
            ;; Read welcome message
            (let [welcome @(s/take! conn 2000)
                  data (json/read-value welcome)]
              (is (= "welcome" (get data "type")))
              (is (= "Welcome to WebSocket!" (get data "message"))))

            ;; Send a message
            @(s/put! conn "Hello WebSocket!")

            ;; Receive echo response
            (let [response @(s/take! conn 2000)
                  data (json/read-value response)]
              (is (= "echo" (get data "type")))
              (is (= "Hello WebSocket!" (get data "message"))))

            ;; Send another message to verify it keeps working
            @(s/put! conn "Second message")
            (let [response @(s/take! conn 2000)
                  data (json/read-value response)]
              (is (= "echo" (get data "type")))
              (is (= "Second message" (get data "message"))))

            (finally
              (s/close! conn))))))))

(deftest websocket-broadcast-test
  (testing "WebSocket broadcast sends messages to all connected clients"
    (test-sys/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              host (th/server->ws-host server)
              client1 @(http/websocket-client (str host "/api/realtime/ws/broadcast"))
              client2 @(http/websocket-client (str host "/api/realtime/ws/broadcast"))]

          (try
            ;; Drain initial messages (welcome + user joined notifications)
            ;; Client 1: welcome, user joined (1 client)
            @(s/take! client1 2000)
            @(s/take! client1 2000)

            ;; Client 2: welcome, user joined (2 clients)
            @(s/take! client2 2000)
            ;; Both clients get notification about client 2 joining
            @(s/take! client1 2000)
            @(s/take! client2 2000)

            ;; Client 1 sends a message
            @(s/put! client1 "Broadcast test message")

            ;; Both clients should receive it
            (let [msg1 @(s/take! client1 2000)
                  msg2 @(s/take! client2 2000)
                  data1 (json/read-value msg1)
                  data2 (json/read-value msg2)]
              (is (= "broadcast" (get data1 "type")))
              (is (= "Broadcast test message" (get data1 "message")))
              (is (= "broadcast" (get data2 "type")))
              (is (= "Broadcast test message" (get data2 "message"))))

            ;; Client 2 sends a message
            @(s/put! client2 "Response from client 2")

            ;; Both clients should receive it
            (let [msg1 @(s/take! client1 2000)
                  msg2 @(s/take! client2 2000)
                  data1 (json/read-value msg1)
                  data2 (json/read-value msg2)]
              (is (= "broadcast" (get data1 "type")))
              (is (= "Response from client 2" (get data1 "message")))
              (is (= "broadcast" (get data2 "type")))
              (is (= "Response from client 2" (get data2 "message"))))

            (finally
              (s/close! client1)
              (s/close! client2))))))))

(deftest websocket-multiple-echo-connections-test
  (testing "Multiple echo connections work independently"
    (test-sys/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              host (th/server->ws-host server)
              conn1 @(http/websocket-client (str host "/api/realtime/ws/echo"))
              conn2 @(http/websocket-client (str host "/api/realtime/ws/echo"))]

          (try
            ;; Drain welcome messages
            @(s/take! conn1 2000)
            @(s/take! conn2 2000)

            ;; Send different messages from each connection
            @(s/put! conn1 "Message from connection 1")
            @(s/put! conn2 "Message from connection 2")

            ;; Each should receive only their own echo
            (let [response1 @(s/take! conn1 2000)
                  response2 @(s/take! conn2 2000)
                  data1 (json/read-value response1)
                  data2 (json/read-value response2)]
              (is (= "Message from connection 1" (get data1 "message")))
              (is (= "Message from connection 2" (get data2 "message"))))

            (finally
              (s/close! conn1)
              (s/close! conn2))))))))

(comment
  (t/run-test websocket-multiple-echo-connections-test)

  ;
  )


;; ;; =============================================================================
;; ;; Server-Sent Events Tests
;; ;; =============================================================================

(deftest sse-counter-test
  (testing "SSE counter endpoint sends periodic counter updates"
    (test-sys/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              host (th/server->http-host server)
              response @(http/get (str host "/api/realtime/sse/events")
                                  {:as :stream})
              body (:body response)
              input-reader (InputStreamReader. body)
              buffered-reader (BufferedReader. input-reader)]
          (println "type of response" (type response))
          (println "type of body" (type body))
          (println "type of reader" (type buffered-reader))
          (println "body closeable?" (closeable? body))
          (println "response closeable?" (closeable? response))
          (println "input-reader closeable?" (closeable? input-reader))
          (println "buffered-reader closeable?" (closeable? buffered-reader))

          (try
            ;; Verify response headers
            (is (str/starts-with? (get-in response [:headers "content-type"]) "text/event-stream"))
            (is (= "no-cache" (get-in response [:headers "cache-control"])))

            ;; Helper to read one SSE event
            (letfn [(read-event []
                      (loop [lines []]
                        (if-let [line (.readLine buffered-reader)]
                          (if (str/blank? line)
                            (when (seq lines)
                              (parse-sse-event (str/join "\n" lines)))
                            (recur (conj lines line)))
                          nil)))]

              ;; Read first event (connected)
              (let [event (read-event)]
                (is (= "connected" (:event event)))
                (is (= "Connected to SSE stream" (get-in event [:data "message"]))))

              ;; Read first counter event
              (let [event (read-event)]
                (is (nil? (:event event))) ; default event type
                (is (number? (get-in event [:data "counter"])))
                (is (= 0 (get-in event [:data "counter"])))
                (is (number? (get-in event [:data "timestamp"]))))

              ;; Read second counter event to verify incrementing
              (let [event (read-event)]
                (is (= 1 (get-in event [:data "counter"]))))

              ;; Read third counter event
              (let [event (read-event)]
                (is (= 2 (get-in event [:data "counter"])))))

            (finally
              (println "closing reader")
              (when (closeable? buffered-reader)
                (.close buffered-reader))
              (when (closeable? input-reader)
                (.close input-reader))
              (when (closeable? body)
                (println "closing body!!")
                (.close body)))))))))

(deftest sse-multi-event-test
  (testing "SSE multi-event endpoint sends different event types"
    (test-sys/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              host (th/server->http-host server)
              response @(http/get (str host "/api/realtime/sse/multi-events")
                                  {:as :stream})
              body (:body response)
              reader (BufferedReader. (InputStreamReader. body))]

          (try
            ;; Helper to read one SSE event
            (letfn [(read-event []
                      (loop [lines []]
                        (if-let [line (.readLine reader)]
                          (if (str/blank? line)
                            (when (seq lines)
                              (parse-sse-event (str/join "\n" lines)))
                            (recur (conj lines line)))
                          nil)))]

              ;; Skip connected event
              (read-event)

              ;; Read first event (should be price-update since i=0 is even)
              (let [event (read-event)]
                (is (= "price-update" (:event event)))
                (is (number? (get-in event [:data "price"])))
                (is (>= (get-in event [:data "price"]) 100))
                (is (< (get-in event [:data "price"]) 150))
                (is (number? (get-in event [:data "timestamp"]))))

              ;; Read second event (should be notification since i=1 is odd)
              (let [event (read-event)]
                (is (= "notification" (:event event)))
                (is (string? (get-in event [:data "message"])))
                (is (str/starts-with? (get-in event [:data "message"]) "Update"))
                (is (number? (get-in event [:data "timestamp"]))))

              ;; Read third event (should be price-update again since i=2 is even)
              (let [event (read-event)]
                (is (= "price-update" (:event event)))
                (is (number? (get-in event [:data "price"])))))

            (finally
              (.close reader))))))))

(deftest sse-connection-cleanup-test
  (testing "SSE connection cleans up properly when closed"
    (test-sys/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              host (th/server->http-host server)
              response @(http/get (str host "/api/realtime/sse/events")
                                  {:as :stream})
              body (:body response)
              reader (BufferedReader. (InputStreamReader. body))]

          (try
            ;; Read connected event
            (loop [lines []]
              (if-let [line (.readLine reader)]
                (when-not (str/blank? line)
                  (println "got a line here" line)
                  (recur (conj lines line)))
                nil))

            ;; Close the connection
            (.close reader)

            ;; Verify reader is closed by trying to read (should throw or return nil)
            (is (thrown? java.io.IOException (.readLine reader)))

            (catch Exception _
              ;; If we get an exception during the test, that's fine
              ;; Just make sure we close the reader
              (.close reader))))))))


;; ;; =============================================================================
;; ;; Simple Collection Tests (Alternative approach)
;; ;; =============================================================================

(deftest simple-websocket-collection-test
  (testing "Collect multiple WebSocket messages using helper function"
    (test-sys/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              host (th/server->ws-host server)
              messages (collect-ws-messages
                        (str host "/api/realtime/ws/echo")
                        1
                        2000)]
          ;; Should get welcome message
          (is (= 1 (count messages)))
          (is (= "welcome" (get (first messages) "type"))))))))

(deftest simple-sse-collection-test
  (testing "Collect SSE events using helper function"
    (test-sys/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              host (th/server->http-host server)
              events (collect-sse-events
                      (str host "/api/realtime/sse/events")
                      4
                      7000)]
          ;; Should get connected + 3 counter events
          (is (= 4 (count events)))
          (is (= "connected" (:event (first events))))
          (is (= 0 (get-in (second events) [:data "counter"])))
          (is (= 1 (get-in (nth events 2) [:data "counter"])))
          (is (= 2 (get-in (nth events 3) [:data "counter"]))))))))

(comment
  (t/run-test simple-sse-collection-test)
  (t/run-tests)

  ;
  )