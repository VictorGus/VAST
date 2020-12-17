(ns app.core
  (:require [clojure.java.io :as io]
            [route-map.core :as rm]
            [cheshire.core :as json]
            [clojure.core.async :refer [go]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.json   :refer [wrap-json-response wrap-json-body]]
            [app.dbcore   :as db]
            [app.manifest :as m]
            [app.loader]
            [app.action]
            [org.httpkit.server :as server]
            [clojure.string :as str])
  (:import [java.io File])
  (:gen-class))

(defn routes [ctx]
  {"meteorological-data" {"$bulk" {:POST   (app.loader/bulk-load-meteorological-data ctx)}
                          [:id]   {:GET    (app.loader/retrieve-meteorological-data ctx)
                                   :DELETE (app.loader/delete-meteorological-data ctx)}
                          :POST   (app.loader/load-meteorological-data ctx)
                          :GET    (app.loader/retrieve-meteorological-data ctx)}
   "sensor-data"         {"$bulk" {:POST   (app.loader/bulk-load-sensor-data ctx)}
                          [:id]   {:GET    (app.loader/retrieve-sensor-data ctx)
                                   :DELETE (app.loader/delete-sensor-data ctx)}
                          :POST (app.loader/load-sensor-data ctx)
                          :GET  (app.loader/retrieve-sensor-data ctx)}
   "factory"            {"$bulk" {:POST (app.loader/bulk-load-factory ctx)}
                         [:id]   {:DELETE (app.loader/delete-factory ctx)}
                          :POST (app.loader/load-factory ctx)
                          :GET  (app.loader/retrieve-factories ctx)}
   "monitor"            {"$bulk" {:POST   (app.loader/bulk-load-monitor ctx)}
                         [:id]   {:DELETE (app.loader/delete-monitor ctx)}
                         :POST (app.loader/load-monitor ctx)
                         :GET  (app.loader/retrieve-monitors ctx)}
   "$measured-chemicals" {:GET (app.action/get-measured-chemicals ctx)}})

(defn params-to-keyword [params]
  (reduce-kv (fn [acc k v]
               (assoc acc (keyword k) v)) {} params))

(defn handler [ctx]
  (fn [{meth :request-method uri :uri :as req}]
    (if-let [res (rm/match [meth uri] (routes ctx))]
      ((:match res) (-> (assoc req :params (params-to-keyword (:params req)))
                        (update-in [:params] merge (:params res))))
      {:status 404 :body {:error "Not found"}})))

(defn preflight
  [{meth :request-method hs :headers :as req}]
  (let [headers (get hs "access-control-request-headers")
        origin (get hs "origin")
        meth  (get hs "access-control-request-method")]
    {:status 200
     :headers {"Access-Control-Allow-Headers" headers
               "Access-Control-Allow-Methods" meth
               "Access-Control-Allow-Origin" origin
               "Access-Control-Allow-Credentials" "true"
               "Access-Control-Expose-Headers" "Location, Transaction-Meta, Content-Location, Category, Content-Type, X-total-count"}}))

(defn allow [resp req]
  (let [origin (get-in req [:headers "origin"])]
    (update resp :headers merge
            {"Access-Control-Allow-Origin" origin
             "Access-Control-Allow-Credentials" "true"
             "Access-Control-Expose-Headers" "Location, Content-Location, Category, Content-Type, X-total-count"})))

(defn mk-handler [dispatch]
  (fn [{headers :headers uri :uri :as req}]
    (if (= :options (:request-method req))
      (preflight req)
      (let [resp (dispatch req)]
        (-> resp (allow req))))))

(defn app [ctx]
  (-> (handler ctx)
      mk-handler
      wrap-json-body
      wrap-params
      wrap-json-response
      wrap-reload))

(defonce state (atom nil))

(defn dev-ctx []
  (:ctx @state))

(defn stop-server []
  (when-not (nil? @state)
    ((:app @state) :timeout 100)
    (reset! state nil)))

(defn start-server []
  (let [app* (app (assoc m/app-config :db/connection db/pool-config))]
    (reset! state {:ctx db/pool-config
                   :app (server/run-server app* {:port (as-> (get-in m/app-config [:app :port]) port
                                                         (cond-> port
                                                           (string? port)
                                                           Integer/parseInt))})})))

(defn restart-server [] (stop-server) (start-server))

(defn -main [& [_ _]]
  (start-server)
  (println "Server started"))

(comment
  (restart-server)


  )
