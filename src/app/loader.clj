(ns app.loader
  (:require
   [dk.ative.docjure.spreadsheet :refer [select-columns select-sheet read-cell
                                         load-workbook row-seq cell-seq]]
   [clojure.data.csv :as csv]
   [app.dbcore :as db]
   [app.filters]
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

(def meteo-filters
  {:date {:none (fn [v] [:is :date_ts nil])
          :some (fn [v] [:is-not :date_ts nil])
          :not  (fn [v] [:not-in [:pg/coalesce [:pg/sql "(date_ts - interval '3 hours')::date::text"] "1900-01-01"]
                         [:pg/params-list (:not v)]])
          :else (fn [v] [:in [:pg/sql "(date_ts - interval '3 hours')::date::text"] [:pg/params-list (:values v)]])}
   :text {:else (fn [v]
                  (let [v (str "%" (:values v) "%")]
                    [:or
                     [:ilike :direction [:pg/param v]]
                     [:ilike :speed [:pg/param v]]
                     [:ilike :elevation [:pg/param v]]
                     [:ilike :id [:pg/param v]]]))}})

(def sensor-filters
  {:date {:none (fn [v] [:is :date_ts nil])
          :some (fn [v] [:is-not :date_ts nil])
          :not  (fn [v] [:not-in [:pg/coalesce [:pg/sql "(date_ts - interval '3 hours')::date::text"] "1900-01-01"]
                         [:pg/params-list (:not v)]])
          :else (fn [v] [:in [:pg/sql "(date_ts - interval '3 hours')::date::text"] [:pg/params-list (:values v)]])}
   :chem {:none (fn [v] [:is :chemical nil])
          :some (fn [v] [:is-not :chemical nil])
          :not  (fn [v] [:not-in [:pg/coalesce ^:pg/fn[:lower :chemical] "MISSED"] [:pg/params-list (:not v)]])
          :else (fn [v] [:in [:pg/coalesce ^:pg/fn[:lower :chemical] "MISSED"] [:pg/params-list (:values v)]])}
   :monitor {:none (fn [v] [:is :monitor nil])
             :some (fn [v] [:is-not :monitor nil])
             :not  (fn [v] [:not-in [:pg/coalesce :monitor "MISSED"] [:pg/params-list (:not v)]])
             :else (fn [v] [:in [:pg/coalesce :monitor "MISSED"] [:pg/params-list (:values v)]])}
   :text {:else (fn [v]
                  (let [v (str "%" (:values v) "%")]
                    [:or
                     [:ilike :reading [:pg/param v]]
                     [:ilike :monitor [:pg/param v]]
                     [:ilike :chemical [:pg/param v]]
                     [:ilike :id [:pg/param v]]]))}})

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
    (.format (java.text.SimpleDateFormat. "yyy-MM-dd HH:mm:ss") inst-date)))

(defn bulk-load-meteorological-data [{{:keys [working-dir]} :app
                                connection :db/connection :as ctx}]
  (fn [{:keys [body headers params] :as request}]
    (if (= (get headers "content-type") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      (try
        (let [{{:keys [columns sheet]} :meteo} xl-sheets
              xls-sheet (->> body load-workbook
                             (select-sheet sheet))
              data      (filter
                         :date
                         (select-columns columns xls-sheet))]

          (when (:overwrite params)
            (db/execute {:truncate :meteo_data} connection))

          (db/insert-multi connection
                           :meteo_data
                           [:id :date_ts :direction :speed :elevation]
                           (map #(as-> % el
                                   (update el :date (fn [v]
                                                      (-> v
                                                          normalize-date-time
                                                          java.sql.Timestamp/valueOf)))
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
                                     (-> body
                                         (update :date_ts #(java.sql.Timestamp/valueOf %))
                                         (select-keys [:direction :speed
                                                       :date_ts :elevation])))]} connection)
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
                                   (update el :date (fn [v]
                                                      (-> v
                                                          normalize-date-time
                                                          java.sql.Timestamp/valueOf)))
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
                                                        :date_ts :reading]))]} connection)
        {:body body
         :status 201}
        (catch Exception e
          {:status 500
           :body (str e)})))))

(defn retrieve-meteorological-data [{connection :db/connection :as ctx}]
  (fn [{:keys [params] :as request}]
    (try
      (if-let [id (:id params)]
        (let [query {:select [:*]
                     :from [:meteo_data]
                     :where [:= :id id]}
              q-result (db/query-first query connection)]
          (if (nil? q-result)
            {:status 404
             :body {:message "Not found"}}
            {:status 200
             :body q-result}))

        (let [filter (app.filters/parse-filter (:q params))
              where (->> filter
                         (reduce (fn [acc [k v]]
                                   (if-let [flt (when-let [f (get meteo-filters k)]
                                                  (cond (:none v) (:none f)
                                                        (:some v) (:some f)
                                                        (:not v) (:not f)
                                                        :else (:else f)))]
                                     (assoc acc k (flt v))
                                     acc)) {}))
              records-count (-> {:select [:%count.*]
                                 :from [:meteo_data]}
                                (db/query-first connection)
                                :count)
              q-result      (db/query (cond-> {:ql/type :pg/select
                                               :select :*
                                               :from :meteo_data
                                               :where where}
                                        (:page params)
                                        (assoc :offset (* (- (Integer/parseInt (:page params)) 1)
                                                          100)
                                               :limit  100)) connection)]
          {:status 200
           :body (cond-> {:total records-count
                          :entry {:data q-result}}
                   (:page params)
                   (assoc :page (:page params) :page-size 100
                          :hasMore? (not (empty? q-result))))}))
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
        (let [filter (app.filters/parse-filter (:q params))
              where (->> filter
                         (reduce (fn [acc [k v]]
                                   (if-let [flt (when-let [f (get sensor-filters k)]
                                                  (cond (:none v) (:none f)
                                                        (:some v) (:some f)
                                                        (:not v) (:not f)
                                                        :else (:else f)))]
                                     (assoc acc k (flt v))
                                     acc)) {}))
              records-count (-> {:select [:%count.*]
                                 :from [:sensor_data]}
                                (db/query-first connection)
                                :count)
              q-result (db/query (cond-> {:ql/type :pg/select
                                          :select :*
                                          :from :sensor_data
                                          :where where}
                                   (:page params)
                                   (assoc :offset (* (- (Integer/parseInt (:page params)) 1)
                                                     100)
                                          :limit  100)) connection)]
          {:status 200
           :body (cond-> {:total records-count
                          :entry {:data q-result}}
                   (:page params)
                   (assoc :page (:page params) :page-size 100
                          :hasMore? (not (empty? q-result))))}))
      (catch Exception e
        {:status 500
         :body (str e)}))))

(defn retrieve-factories [{connection :db/connection :as ctx}]
  (fn [{:keys [params] :as request}]
    (try
      (if-let [id (:id params)]
        (let [query {:select [:*]
                     :from [:factory]
                     :where [:= :id id]}
              q-result (db/query-first query connection)]
          (if (nil? q-result)
            {:status 404
             :body {:message "Not found"}}
            {:status 200
             :body q-result}))
        (let [records-count (-> {:select [:%count.*]
                                 :from [:factory]}
                                (db/query-first connection)
                                :count)
              q-result      (db/query {:ql/type :pg/select
                                       :select :*
                                       :from :factory} connection)]
          {:status 200
           :body  {:total records-count
                   :entry {:data q-result}}}))
      (catch Exception e
        {:status 500
         :body (str e)}))))

(defn load-factory [{connection :db/connection :as ctx}]
  (fn [{:keys [body] :as request}]
    (let [body (clojure.walk/keywordize-keys body)]
      (try
        (db/execute {:insert-into :factory
                     :values [(merge {:id (uuid)}
                                     (select-keys body [:factory_name :longitude
                                                        :latitude :description]))]} connection)
        {:body body
         :status 201}
        (catch Exception e
          {:status 500
           :body (str e)})))))

(defn retrieve-monitors [{connection :db/connection :as ctx}]
  (fn [{:keys [params] :as request}]
    (try
      (if-let [id (:id params)]
        (let [query {:select [:*]
                     :from [:monitor]
                     :where [:= :id id]}
              q-result (db/query-first query connection)]
          (if (nil? q-result)
            {:status 404
             :body {:message "Not found"}}
            {:status 200
             :body q-result}))
        (let [records-count (-> {:select [:%count.*]
                                 :from [:monitor]}
                                (db/query-first connection)
                                :count)
              q-result      (db/query {:ql/type :pg/select
                                       :select :*
                                       :from :monitor} connection)]
          {:status 200
           :body  {:total records-count
                   :entry {:data q-result}}}))
      (catch Exception e
        {:status 500
         :body (str e)}))))

(defn delete-factory [{connection :db/connection :as ctx}]
  (fn [{:keys [params] :as request}]
    (try
      (let [q-result (db/execute {:delete-from :factory
                                  :where [:= :id (:id params)]} connection)]
        (if (> q-result 0)
          {:status 200
           :body {:message "ok"}}
          {:status 404
           :body {:message "Not found"}}))
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
  (update {:a "2004-10-19 10:23:54"} :a #(java.sql.Timestamp/valueOf %))

  )
