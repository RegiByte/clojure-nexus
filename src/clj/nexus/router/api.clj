(ns nexus.router.api
  (:require
   [clojure.java.io :as io]
   [reitit.openapi :as openapi]
   [reitit.ring.malli :as malli]))

(defn routes
  "Main delegator for API routes"
  []
  [["/health" {:name :api/health
               :get {:summary "Pings the server and check if everything is okay"
                     :handler (fn [_request]
                                (tap> _request)
                                {:status 200
                                 :body {:message "Healthy!"}})}}]
   ["/crash" {:get {:no-doc true
                    :handler (fn [_request]
                               (throw (ex-info "Purposely crashed from route" {:exception-info :nothing?}))
                               {:status 200
                                :body {:message "Should crash bro"}})}}]

   ["/openapi.json", {:get
                      {:no-doc true
                       :openapi {:info {:title "Nexus API"
                                        :description "A showcase of how to build a production-ready web server in clojure"
                                        :version "0.0.1"}
                                 :components {:securitySchemes {"auth" {:type :apiKey
                                                                        :in :header
                                                                        :name "x-api-key"}}}}
                       :handler (openapi/create-openapi-handler)}}]

   ["/secure"
    {:tags #{"secure"}
     :openapi {:security [{"auth" []}]}
     :swagger {:security [{"auth" []}]}}
    ["/get"
     {:get {:summary "endpoint authenticated with a header"
            :responses {200 {:body [:map [:secret :string]]}
                        401 {:body [:map [:error :string]]}}
            :handler (fn [request]
                       ;; In a real app authentication would be handled by middleware
                       (if (= "secret" (get-in request [:headers "x-api-key"]))
                         {:status 200
                          :body {:secret "I am a marmot"}}
                         {:status 401
                          :body {:error "unauthorized"}}))}}]]

   ["/math"
    {:tags #{"math"}}

    ["/plus"
     {:get {:summary "plus with malli query parameters"
            :parameters {:query [:map
                                 [:x
                                  {:title "X parameter"
                                   :description "Description for X parameter"
                                   :json-schema/default 42}
                                  int?]
                                 [:y {:title "Y paramater"
                                      :description "These attributes are not required"} int?]]}
            :responses {200 {:body [:map [:total int?]]}}
            :handler (fn [{{{:keys [x y]} :query} :parameters :as request}]
                       (tap> {:x x :y y
                              :request request})
                       {:status 200
                        :body {:total (+ x y)}})}

      :post {:summary "plus with malli body parameters"
             :parameters {:body [:map
                                 [:x
                                  {:title "X parameter"
                                   :description "Description for X parameter"
                                   :json-schema/default 42}
                                  int?]
                                 [:y int?]]}
             :responses {200 {:body [:map [:total int?]]}}
             :handler (fn [{{{:keys [x y]} :body} :parameters}]
                        {:status 200
                         :body {:total (+ x y)}})}}]]

   ["/files"
    {:tags #{"files"}}

    ["/upload"
     {:post {:summary "upload a file"
             :parameters {:multipart [:map [:file malli/temp-file-part]]}
             :responses {200 {:body [:map [:name string?] [:size int?]]}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters :as request}]
                        (tap> request)
                        ;; Create uploads directory if it doesn't exist
                        (let [upload-dir (io/file "uploads")
                              _ (.mkdirs upload-dir)
                              unique-id (str (java.util.UUID/randomUUID))
                              original-filename (:filename file)
                              ;; create destination file
                              dest-file (io/file upload-dir (str unique-id "--" original-filename))
                              ;; Copy the temp file to the destination
                              _ (io/copy (:tempfile file) dest-file)]
                          {:status 200
                           :body {:name (:filename file)
                                  :size (:size file)}}))}}]

    ["/download"
     {:get {:summary "downloads a file"
            :swagger {:produces ["image/png"]}
            :responses {200 {:description "an image"
                             :content {"image/png" {:schema string?}}}}
            :handler (fn [_]
                       {:status 200
                        :headers {"Content-Type" "image/png"}
                        :body (-> "public/favicon-32x32.png"
                                  (io/resource)
                                  (io/input-stream))})}}]]
   ;
   ])