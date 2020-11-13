(ns zframes.modal
  (:require [re-frame.core :as rf]
            [app.styles :as styles]))

(rf/reg-event-db
 ::modal
 (fn [db [_ modal]]
   (assoc db :modal modal)))

(rf/reg-event-fx
 ::close-modal
 (fn [{:keys [db]} [_ {:keys [success]}]]
   {:db       (dissoc db :modal)
    :dispatch [(:event success) (:params success)]}))

(rf/reg-sub
 ::modal
 (fn [db _]
   (:modal db)))
