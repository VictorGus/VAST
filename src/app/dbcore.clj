(ns app.dbcore
  (:require [honeysql.core       :as hsql]
            [hikari-cp.core      :as hc]
            [clojure.java.jdbc   :as jdbc]
            [clj-postgresql.core :as pg]
            [dsql.pg             :as dsql]
            [app.manifest        :as m]))

(def pool-config (delay (pg/pool :host     (get-in m/app-config [:db :host])
                                 :port     (get-in m/app-config [:db :port])
                                 :user     (get-in m/app-config [:db :user])
                                 :password (get-in m/app-config [:db :password])
                                 :dbname   (get-in m/app-config [:db :dbname])
                                 :hikari {:read-only true})))

(def test-config (delay (pg/pool :host     (get-in m/app-config [:db :host])
                                 :port     (get-in m/app-config [:db :port])
                                 :user     (get-in m/app-config [:db :user])
                                 :password (get-in m/app-config [:db :password])
                                 :dbname   "fortest"
                                 :hikari {:read-only true})))


(defn query [query ctx]
  (cond
    (or (string? query) (vector? query))
    (jdbc/query @ctx query)

    (and (map? query) (:ql/type query))
    (->> query dsql/format (jdbc/query @ctx))

    :else
    (->> query hsql/format (jdbc/query @ctx))))

(defn query-first [query ctx]
  (cond
    (or (string? query) (vector? query))
    (first (jdbc/query @ctx query))

    (and (map? query) (:ql/type query))
    (->> query dsql/format (jdbc/query @ctx) first)

    :else
    (->> query hsql/format (jdbc/query @ctx) first)))

(defn execute [query ctx]
  (cond
    (or (string? query) (vector? query))
    (first (jdbc/execute! @ctx query))

    (and (map? query) (:ql/type query))
    (->> query dsql/format (jdbc/execute! @ctx) first)

    :else
    (->> query hsql/format (jdbc/execute! @ctx) first)))

(defn execute-test [query]
  (->> query hsql/format
       (jdbc/execute! @test-config)
       first))

(defn truncate-test []
  (jdbc/execute! @test-config "truncate patient"))

(defn insert-multi [ctx table columns data]
  (jdbc/insert-multi! @ctx table columns data))
