(ns app.data-grid.form
  (:require [re-frame.core      :as rf]
            [zenform.model      :as zf]
            [zenform.validators :as validators]))

(def form-path [:form :modal])

(def form-schema
  {:type :form
   :fields {:wind_direction  {:type :string}
            :wind_speed      {:type :string}
            :elevation       {:type :string}
            :date_ts         {:type :string}}})

(rf/reg-event-fx
 ::init
 (fn [{db :db} [_ {:keys [resource]}]]
   {:dispatch [:zf/init form-path form-schema resource]}))

(defn eval-form [db fx]
  (let [{:keys [errors form value]} (->> form-path (get-in db) zf/eval-form)
        content (-> value
                    (assoc :name [(merge (select-keys value [:family])
                                         {:given [(:given value) (:patronymic value)]})])
                    (dissoc :family :given :patronymic))]
    (if (empty? errors)
      (fx content)
      {:db (assoc-in db form-path form)
       :dispatch [:flash/danger {:msg (-> errors vals first :required)}]})))


(rf/reg-event-fx
 ::deinit
 (fn [{db :db}]
   {:db (assoc-in db form-path nil)}))
