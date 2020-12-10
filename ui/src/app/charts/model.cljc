(ns app.charts.model
  (:require [re-frame.core :as rf]))

(def index ::index)

(rf/reg-sub
 index
 (fn [db]
   db))

(rf/reg-event-fx
 index
 (fn [{db :db} [pid phase {params :params}]]
   (cond
     (= phase :deinit)
     {}
     (= phase :init)
     {})))
