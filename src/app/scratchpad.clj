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

  (dsql/format
   {:ql/type :pg/select
    :select :*
    :from :sensor_data
    :limit 10})

  )

