(ns app.loader
  (:require
   [dk.ative.docjure.spreadsheet :refer [select-columns select-sheet read-cell
                                         load-workbook row-seq cell-seq]]
   [clojure.data.csv :as csv]
   [app.dbcore :as db]
   [honeysql.core :as hsql]
   [clojure.java.io :as io]))

(def xl-sheets
  {:meteo {:columns {:A :date
                     :B :wind-direction
                     :C :wind-speed
                     :D :elevation}
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
  (fn [{:keys [body] :as request}]
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
         :body (str e)}))))

(defn load-meteorological-data [{connection :db/connection :as ctx}]
  (fn [{:keys [body] :as request}]
    (let [body (clojure.walk/keywordize-keys body)]
      (db/query {:insert-into :patient
                 :values [(merge {:id (uuid)}
                                 (select-keys body [:wind-direction :wind-speed
                                                    :date :elevation]))]} connection)
      (try
        {:body body
         :status 201}
        (catch Exception e
          {:status 500
           :body (str e)})))))

(defn retrieve-meteorological-data [ctx]
  (fn [request]
    (try
      (println "test")
      (catch Exception e
        {:status 500
         :body (str e)}))))

(comment


  )
