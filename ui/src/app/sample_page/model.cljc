(ns app.sample-page.model
  (:require [re-frame.core :as rf]))

(def index ::sample)

(rf/reg-event-fx
 index
 (fn [{db :db} [pid]]
   {}))

(rf/reg-sub
 index
 (fn [db]
   {}))
