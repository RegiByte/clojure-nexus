(ns nexus.test.helpers
  (:require
   [clojure.edn :as edn]
   [jsonista.core :as json]
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