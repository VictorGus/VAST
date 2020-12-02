(ns app.loader
  (:require
   [dk.ative.docjure.spreadsheet :refer [select-columns select-sheet read-cell
                                         load-workbook row-seq cell-seq]]
   [clojure.data.csv :as csv]
   [dsql.pg :as dsql]
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

(def filters
  {:date {:none (fn [v] [:is :date_ts nil])
          :some (fn [v] [:is-not :date_ts nil])
          :not  (fn [v] [:not-in [:pg/coalesce [:pg/sql "(date_ts - interval '3 hours')::date::text"] "1900-01-01"]
                         [:pg/params-list (:not v)]])
          :else (fn [v] [:in [:pg/sql "(date_ts - interval '3 hours')::date::text"] [:pg/params-list (:values v)]])}

   :status {:none (fn [v] [:is [:resource->> :status] nil])
            :some (fn [v] [:is-not [:resource->> :status] nil])
            :not  (fn [v]
                    [:not-in [:pg/coalesce [:resource->> :status] "MISSED"] [:pg/params-list (:not v)]])
            :else (fn [v] [:in [:resource->> :status] [:pg/params-list (:values v)]])}

   :location {:none (fn [v] [:is [:resource->> :location] nil])
              :some (fn [v] [:is-not [:resource->> :location] nil])
              :not  (fn [v] [:not-in [:resource#>> [:location :id]] [:pg/params-list (:not v)]])
              :else (fn [v]
                      [:in [:resource#>> [:location :id]] [:pg/params-list (:values v)]])}

   :division {:not (fn [v] [:not-in
                             [:resource#>> [:location :id]]
                             {:ql/type :pg/sub-select
                              :select :id
                              :from :Department
                              :where [:in [:resource#>> [:part_of :id]] [:pg/params-list (:not v)]]}])
              :else (fn [v] [:in
                             [:resource#>> [:location :id]]
                             {:ql/type :pg/sub-select
                              :select :id
                              :from :Department
                              :where [:in [:resource#>> [:part_of :id]] [:pg/params-list (:values v)]]}])}

   :tag  {:none (fn [v] [:is [:resource->> :tags] nil])
          :some (fn [v] [:is-not [:resource->> :tags] nil])
          :not (fn [v]
                 [:or [:is [:resource-> :tags] nil]
                  [:not [:pg/include-op [:resource-> :tags]
                         (into ^:jsonb/array[] (:not v))]]])
          :else (fn [v]
                  [:pg/include-op [:resource-> :tags]
                   (into ^:jsonb/array[] (:values v))])}

   :text  {:else (fn [v]
                   (let [v (str "%" (:values v) "%")]
                     [:or
                      [:ilike [:pg/sql "resource::text"] [:pg/param v]]
                      [:ilike :id [:pg/param v]]]))}

   :id {:else (fn [v]
                ^:pg/op[:in :id
                          [:pg/params-list (vec (:values v))]])
        :range (fn [{[start end] :range}]
                 ^:pg/op[:and
                         ^:pg/op[:>= :id [:pg/param start]]
                         ^:pg/op[:<= :id [:pg/param end]]])
        :not-in-range (fn [{[start end] :not-in-range}]
                        [:not-with-parens ^:pg/op[:and
                                                  ^:pg/op[:>= :id [:pg/param start]]
                                                  ^:pg/op[:<= :id [:pg/param end]]]])}

   ;;:route {:none (fn [v] [:is [:resource->> :primary_payer] nil])
   ;;        :some (fn [v] [:is-not [:resource->> :primary_payer] nil])
   ;;        :not  (fn [v] [:not-in [:pg/coalesce [:resource->> :primary_payer] "MISSED"] [:pg/params-list (:not v)]])
   ;;        :else (fn [v] [:in [:resource->> :primary_payer] [:pg/params-list (:values v)]])}
   ;;
   ;;:ws    {:else (fn [{vs :values}]
   ;;                [:in :id {:ql/type :pg/sub-select
   ;;                          :select [:pg/sql "jsonb_array_elements_text(resource->'case_ids')"]
   ;;                          :from :billingcaseworkset
   ;;                          :where [:in :id [:pg/params-list vs]]}])}

   :client    {:else (fn [{vs :values}]
                       [:in [:resource#>> [:client :id]] [:pg/params-list vs]])
               :some (fn [v] [:is-not [:resource#>> [:client :id]] nil])
               :none (fn [v] [:is [:resource#>> [:client :id]] nil])}

   :patient   {:else (fn [{vs :values}]
                       [:or
                        [:ilike [:resource#>> [:patient :display]] (first vs)]
                        [:in [:resource#>> [:patient :id]] [:pg/params-list vs]]])
               :some (fn [v] [:is-not [:resource#>> [:patient :id]] nil])
               :none (fn [v] [:is [:resource#>> [:patient :id]] nil])}

   :assignee    {:else (fn [{vs :values}]
                         [:in [:resource#>> [:assignee :id]] [:pg/params-list vs]])
                 :not  (fn [{vs :not}]
                         [:or
                          [:is [:resource#>> [:assignee :id]] nil]
                          [:not [:in [:resource#>> [:assignee :id]] [:pg/params-list vs]]]])
                 :some (fn [v] [:is-not [:resource#>> [:assignee :id]] nil])
                 :none (fn [v] [:is [:resource#>> [:assignee :id]] nil])}

   :ins {:some (fn [v] [:is-not [:resource#>> [:coverages :primary]] nil])
         :none (fn [v] [:is [:resource#>> [:coverages :primary]] nil])
         :else (fn [v]
                 (let [value (first (:values v))]
                   (cond
                     (re-matches #"\d+" value) [:or [:= [:pg/param value] [:pg/sql "((knife_extract_text(resource, '[[\"coverages\",\"primary\",\"resource\",\"plan\",\"identifier\",{\"system\": \"amd\"},\"value\"]]'))[1])"]]
                                                [:ilike [:pg/param value] [:jsonb/#>> :resource [:coverages :primary :resource :plan :id]]]]
                     (= "!some" value) [:is-not [:jsonb/#>> :resource [:coverages :primary :resource :eligible]] nil]
                     (= "!succ" value) [:= [:pg/param true] [:pg/sql "(resource#>'{coverages,primary,resource,eligible}')::bool"]]
                     (= "!fail" value) [:= [:pg/param false] [:pg/sql "(resource#>'{coverages,primary,resource,eligible}')::bool"]]
                     (= "!none" value) [:is [:jsonb/#>> :resource [:coverages :primary :resource :eligible]] nil]
                     :else [:or [:ilike [:resource#>> [:coverages :primary :resource :plan :display]] [:pg/param (str "%" value "%")]]
                                [:ilike [:pg/param value] [:jsonb/#>> :resource [:coverages :primary :resource :plan :id]]]])))}


   :ins2 {:some (fn [v] [:is-not [:resource#>> [:coverages :secondary]] nil])
          :none (fn [v] [:is [:resource#>> [:coverages :secondary]] nil])
          :else (fn [v]
                  (let [value (first (:values v))]
                    (cond
                      (re-matches #"\d+" value) [:= [:pg/param value] [:pg/sql "((knife_extract_text(resource, '[[\"coverages\",\"secondary\",\"resource\",\"plan\",\"identifier\",{\"system\": \"amd\"},\"value\"]]'))[1])"]]
                      (= "!some" value) [:is-not [:jsonb/#>> :resource [:coverages :secondary :resource :eligible]] nil]
                      (= "!succ" value) [:= [:pg/param true] [:pg/sql "(resource#>'{coverages,secondary,resource,eligible}')::bool"]]
                      (= "!fail" value) [:= [:pg/param false] [:pg/sql "(resource#>'{coverages,secondary,resource,eligible}')::bool"]]
                      (= "!none" value) [:is [:jsonb/#>> :resource [:coverages :secondary :resource :eligible]] nil]
                      :else [:ilike [:resource#>> [:coverages :secondary :resource :plan :display]] [:pg/param (str "%" value "%")]])))}

   :problems {:else (fn [{v :values}]
                      (cond
                        (= "availity" (first v))
                        [:pg/sql "((knife_extract_text((select h.resource from healthplan h where h.id = billingcase.resource#>>'{coverages,primary,resource,plan,id}'),
                           '[[\"identifier\",{\"system\": \"availity\"},\"value\"]]'))[1]) is null and  billingcase.resource#>>'{coverages,primary,resource,plan,id}' is not null"]
                        (= "npi" (first v))
                        [:pg/sql "(select p.resource#>>'{usnpi, status}' from practitioner p where id = billingcase.resource#>>'{referring_provider, id}') = 'invalid'"]
                        ))}

   :coding {:some (fn [v] [:is-not [:jsonb/#>> :resource [:coding]] nil])
            :none (fn [v] [:is [:jsonb/#>> :resource [:coding]] nil])}})

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
                                   (if-let [flt (when-let [f (get filters k)]
                                                  (cond (:none v) (:none f)
                                                        (:range v) (:range f)
                                                        (:not-in-range v)
                                                        (:not-in-range f)
                                                        (:some v) (:some f)
                                                        (:not v) (:not f)
                                                        :else (:else f)))]
                                     (assoc acc k (flt v))
                                     acc)) {}))
              ;; _ (prn where)
              records-count (-> {:select [:%count.*]
                                 :from [:meteo_data]}
                                (db/query-first connection)
                                :count)
              ;; _ (prn "AAAA" (dsql/format (cond-> {:ql/type :pg/select
              ;;                                     :select :*
              ;;                                     :from :meteo_data
              ;;                                     :where where
              ;;                                     }
              ;;                              (:page params)
              ;;                              (assoc :offset (* (- (Integer/parseInt (:page params)) 1)
              ;;                                                100)
              ;;                                   :limit  100))))
              q-result      (db/query (cond-> {:ql/type :pg/select
                                               :select :*
                                               :from :meteo_data
                                               :where where
                                               }
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
        (let [records-count (-> {:select [:%count.*]
                                 :from [:sensor_data]}
                                (db/query-first connection)
                                :count)
              q-result (db/query (cond-> {:select [:*]
                                          :from [:sensor_data]}
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
