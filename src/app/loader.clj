(ns app.loader
  (:require
   [dk.ative.docjure.spreadsheet :refer [select-columns select-sheet read-cell
                                         load-workbook row-seq cell-seq]]
   [clojure.data.csv :as csv]
   [app.dbcore :as db]
   [honeysql.core :as hsql]
   [clojure.java.io :as io]))

(def xl-sheets
  {:meteo  {:columns {:A :date
                      :B :wind-direction
                      :C :wind-speed
                      :D :elevation}
            :sheet   "Sheet1"}
   :sensor {:columns {:A :chemical
                      :B :monitor
                      :C :date
                      :D :reading}
            :sheet   "Sheet1"}})

(defn uuid []
  (str (java.util.UUID/randomUUID)))

;; (defn load-csv [working-dir body]
;;   (let [data (->> body row-seq
;;                   (remove nil?)
;;                   (map cell-seq)
;;                   (map #(->> %
;;                              (map read-cell)
;;                              (cons (uuid))
;;                              vec))
;;                   )]
;;     (with-open [writer (io/writer (str working-dir "/data.csv"))]
;;       (csv/write-csv writer data))))

(defn normalize-date-time [inst-date]
  (when inst-date
    (.format (java.text.SimpleDateFormat. "yyy-MM-dd'T'HH:mm:ss") inst-date)))

(defn bulk-load-meteorological-data [{{:keys [working-dir]} :app
                                connection :db/connection :as ctx}]
  (fn [{:keys [body headers] :as request}]
    (if (= (get headers "content-type") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      (try
        (let [{{:keys [columns sheet]} :meteo} xl-sheets
              xls-sheet (->> body load-workbook
                             (select-sheet sheet))
              data      (select-columns columns xls-sheet)]
          (db/insert-multi connection
                           :meteo_data
                           [:id :date_ts :direction :speed :elevation]
                           (map #(as-> % el
                                   (update el :date normalize-date-time)
                                   (vals el)
                                   (map (fn [e]
                                          (if (nil? e)
                                            "unknown"
                                            e)) el)
                                   (cons (uuid) el)) (drop 1 data)))
          {:body {:message (str (count data) " records have been loaded")}
           :status 201})
        (catch Exception e
          {:status 500
           :body (str e)}))
      {:status 415
       :body {:message "Not supported type of request body"}})))

(defn load-meteorological-data [{connection :db/connection :as ctx}]
  (fn [{:keys [body] :as request}]
    (let [body (clojure.walk/keywordize-keys body)]
      (try
        (db/execute {:insert-into :meteo_data
                     :values [(merge {:id (uuid)}
                                     (select-keys body [:direction :speed
                                                        :date_ts :elevation]))]} connection)
        {:body body
         :status 201}
        (catch Exception e
          {:status 500
           :body (str e)})))))

(defn bulk-load-sensor-data [{{:keys [working-dir]} :app
                              connection :db/connection :as ctx}]
  (fn [{:keys [body headers] :as request}]
    (if (= (get headers "content-type") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      (try
        (let [{{:keys [columns sheet]} :sensor} xl-sheets
              xls-sheet (->> body load-workbook
                             (select-sheet sheet))
              data      (select-columns columns xls-sheet)]
          (db/insert-multi connection
                           :sensor_data
                           [:id :chemical :monitor :date_ts :reading]
                           (map #(as-> % el
                                   (update el :date normalize-date-time)
                                   (vals el)
                                   (map (fn [e]
                                          (if (nil? e)
                                            "unknown"
                                            e)) el)
                                   (cons (uuid) el)) (drop 1 data)))
          {:body {:message (str (count data) " records have been loaded")}
           :status 201})
        (catch Exception e
          {:status 500
           :body (str e)}))
      {:status 415
       :body {:message "Not supported type of request body"}})))

(defn load-sensor-data [{connection :db/connection :as ctx}]
  (fn [{:keys [body] :as request}]
    (let [body (clojure.walk/keywordize-keys body)]
      (try
        (db/execute {:insert-into :sensor_data
                     :values [(merge {:id (uuid)}
                                     (select-keys body [:monitor :chemical
                                                        :date :reading]))]} connection)
        {:body body
         :status 201}
        (catch Exception e
          {:status 500
           :body (str e)})))))

(defn retrieve-meteorological-data [{connection :db/connection :as ctx}]
  (fn [{:keys [params] :as request}]
    (try
      (if-let [id (:id params)]
        (let [q-result (db/query-first {:select [:*]
                                        :from [:meteo_data]
                                        :where [:=
                                                :id
                                                id]} connection)]

          (if (nil? q-result)
            {:status 404
             :body {:message "Not found"}}
            {:status 200
             :body q-result}))
        (let [q-result (db/query {:select [:*]
                                  :from [:meteo_data]} connection)]
          {:status 200
           :body {:entry {:data q-result}}}))
      (catch Exception e
        {:status 500
         :body (str e)}))))

(defn retrieve-sensor-data [{connection :db/connection :as ctx}]
  (fn [{:keys [params] :as request}]
    (try
      (if-let [id (:id params)]
        (let [q-result (db/query-first {:select [:*]
                                        :from [:sensor_data]
                                        :where [:=
                                                :id
                                                id]} connection)]
          (if (nil? q-result)
            {:status 404
             :body {:message "Not found"}}
            {:status 200
             :body q-result}))
        (let [q-result (db/query {:select [:*]
                                  :from [:sensor_data]} connection)]
          {:status 200
           :body {:entry {:data q-result}}}))
      (catch Exception e
        {:status 500
         :body (str e)}))))

(defn delete-meteorological-data [{connection :db/connection :as ctx}]
  (fn [{:keys [params] :as request}]
    (try
      (let [q-result (db/execute {:delete-from :meteo_data
                                  :where [:= :id (:id params)]} connection)]
        (if (> q-result 0)
          {:status 200
           :body {:message "ok"}}
          {:status 404
           :body {:message "Not found"}}))
      (catch Exception e
        {:status 500
         :body (str e)}))))


(defn delete-sensor-data [{connection :db/connection :as ctx}]
  (fn [{:keys [params] :as request}]
    (try
      (let [q-result (db/execute {:delete-from :sensor_data
                                  :where [:= :id (:id params)]} connection)]
        (if (> q-result 0)
          {:status 200
           :body {:message "ok"}}
          {:status 404
           :body {:message "Not found"}}))
      (catch Exception e
        {:status 500
         :body (str e)}))))

(comment


  )
