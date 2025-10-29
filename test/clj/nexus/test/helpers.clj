(ns nexus.test.helpers
  (:require
   [clojure.edn :as edn]
   [jsonista.core :as json]
   [clj-http.client :as http]
   [clojure.java.io :as io]))


(defn decode-body
  "Decodes response body from InputStream to Clojure data.
   Handles JSON, EDN, and plain strings."
  [response]
  (let [body (:body response)]
    (cond
      ;; Already decoded
      (or (map? body) (vector? body)) body

      ;; InputStream - decode based on content-type
      (instance? java.io.InputStream body)
      (let [content-type (get-in response [:headers "Content-Type"] "application/json")
            body-str (slurp (io/reader body))]
        (cond
          (or (.contains content-type "application/json")
              (.contains content-type "json"))
          (json/read-value body-str json/keyword-keys-object-mapper)

          (.contains content-type "application/edn")
          (edn/read-string body-str)

          :else body-str))

      ;; String body
      (string? body) body

      ;; Nil or unknown
      :else body)))

(defn parse-response
  "Parses a Ring response, decoding the body"
  [response]
  (-> response
      (as-> res (update-in res [:body] (decode-body res)))
      ; add more transformations here as needed
      ))

;; Convenience wrapper
(defn call-handler
  "Calls a Ring handler and decodes the response"
  [handler request]
  (-> (handler request)
      parse-response))


(defn server->port
  "Extracts the actual port from a running Jetty server instance.
   Useful when the server is started on port 0 (random port)."
  [server]
  (-> server
      (.getConnectors)
      (first)
      (.getLocalPort)))

(defn server->host
  "Extracts the actual port from a running Jetty server instance.
   Useful when the server is started on port 0 (random port)."
  [server]
  (let [port (server->port server)]
    (str "http://localhost:" port)))

(defn http-request
  "Makes an HTTP request using clj-http.
   
   Parameters:
   - method: :get, :post, :put, :delete, :patch, :head, :options
   - url: Full URL including protocol, host, and port
   - options: Optional map with:
     - :headers - Map of headers
     - :body - Request body (string or map, will be JSON encoded if map)
     - :query-params - Map of query parameters
     - :form-params - Map of form parameters
     - :as - Response format (:json, :json-string-keys, :auto, :text, etc.)
   
   Examples:
     (http-request :get \"http://localhost:8080/api/health\")
     (http-request :post \"http://localhost:8080/api/users\" 
                   {:body {:name \"John\"} 
                    :headers {\"Authorization\" \"Bearer token\"}})"
  ([method url] (http-request method url {}))
  ([method url options]
   (let [default-opts {:as :json
                       :content-type :json
                       :accept :json
                       :throw-exceptions false  ; Don't throw on 4xx/5xx
                       :coerce :always}         ; Always try to parse JSON
         ;; If body is a map, JSON-encode it
         options (if (and (map? (:body options))
                          (not (string? (:body options))))
                   (update options :body json/write-value-as-string)
                   options)
         opts (merge default-opts options)]
     (case method
       :get (http/get url opts)
       :post (http/post url opts)
       :put (http/put url opts)
       :delete (http/delete url opts)
       :patch (http/patch url opts)
       :head (http/head url opts)
       :options (http/options url opts)))))