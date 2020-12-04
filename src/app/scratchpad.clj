(ns app.scratchpad
  (:require [app.core]
            [app.dbcore :as db]
            [dsql.pg :as dsql]
            #_[clojure.java.jdbc   :as jdbc]))

(comment

  (def dev-ctx (app.core/dev-ctx))

  dev-ctx

  (db/query {:select [:*]
             :from [:sensor_data]
             :limit 10}
            dev-ctx)

  (db/query {:ql/type :pg/select
             :select :*
             :from :sensor_data
             :limit 10}
            dev-ctx)

  (db/query ["select * from sensor_data limit ?" 10] dev-ctx)

  (db/query "select * from sensor_data limit 10" dev-ctx)

  (db/query {:ql/type :pg/select
             :select :m.direction
             :from {:m :meteo_data}
             :where [:= :m.direction [:pg/param "327"]]
             :offset 100
             :limit 1}
            dev-ctx)

  (db/query {:ql/type :pg/select
             :select :* #_[:pg/sql "(date_ts - interval '3 hours')::date::text"]
             :from :meteo_data
             :where [:in [:pg/sql "(date_ts - interval '3 hours')::date::text"] [:pg/params-list ["2016-03-31"]]]
             ;; :where [:= :id "39b83d1a-705d-4177-a434-eac045f6eb05"]
             :offset 0
             :limit 100
             } dev-ctx)

  (db/query ["SELECT * FROM meteo_data WHERE (date_ts - interval '3 hours')::date::text IN ( ? ) LIMIT 100 OFFSET 0" "2016-03-31"]
   #_["SELECT * FROM meteo_data WHERE /*date*/ (date_ts - interval '3 hours')::date::text IN ( ? ) LIMIT 100 OFFSET 0" "2020-03-31"]
            dev-ctx)

;; => ({:date_ts #inst "2016-03-31T21:00:00.000-00:00"})

  (dsql/format {:ql/type :pg/select
                :select :* #_[:pg/sql "(date_ts - interval '3 hours')::date::text"]
                :from :meteo_data
                :where [:in [:pg/sql "(date_ts - interval '3 hours')::date::text"] [:pg/params-list ["2016-03-31"]]]
                ;; :where [:= :id "39b83d1a-705d-4177-a434-eac045f6eb05"]
                :offset 0
                :limit 100
                })
;; => ["SELECT * FROM meteo_data WHERE (date_ts - interval '3 hours')::date::text IN ( ? ) LIMIT 100 OFFSET 0"
;;     "2016-03-31"]
  (dsql/format
   {:ql/type :pg/select
    :select :*
    :from :sensor_data
    :limit 10})

  (dsql/format
   {:ql/type :pg/select
    :select ^:pg/fn[:lower :direction]
    :from :meteo_data
    :where [:= :direction [:pg/param "327"]]
    :offset 100
    :limit 1}
   )

  )

