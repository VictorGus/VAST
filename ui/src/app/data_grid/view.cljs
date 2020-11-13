(ns app.data-grid.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [zframes.modal :as modal]
            [app.inputs :as inputs]
            [app.data-grid.form :as form]
            [app.pages :as pages]
            [app.styles :as styles]
            [app.data-grid.model :as model]))

(def grid-styles
  (styles/style
   [:.pointer {:cursor "pointer"}]
   [:.tab:hover
    {:background-color "#f7fafc"}]
   [:.tab-active
    {:background-color "#f7fafc"}]
   [:.grid-record:hover
    {:background-color "#f7fafc"}]))

(defn form-items [items]
  [:div.row
   (for [i items]
     [:div.col-6.mt-2
      [:label.text-muted (:label i)]
      [(cond
         (= :text (:type i))
         inputs/text-input

         (= :date (:type i))
         inputs/date-input)
       form/form-path (:form-path i)]])])

(defn modal-form [current-tab]
  [:<>
   [form-items (cond
                 (= :meteo current-tab)
                 [{:label "Wind direction"
                   :type :text
                   :form-path [:wind_direction]}
                  {:label "Wind speed"
                   :type :text
                   :form-path [:wind_speed]}
                  {:label "Elevation"
                   :type :text
                   :form-path [:elevation]}
                  {:label "Date"
                   :type :date
                   :form-path [:date_ts]}]

                 (= :sensor current-tab)
                 [{:label "Chemical"
                   :type :text
                   :form-path [:wind_direction]}
                  {:label "Monitor"
                   :type :text
                   :form-path [:wind_speed]}
                  {:label "Reading"
                   :type :text
                   :form-path [:elevation]}
                  {:label "Date"
                   :type :date
                   :form-path [:date_ts]}])]
   [:div.btn.btn-primary.mt-5 "Create record"]])

(defn meteo-data-grid [data]
  (fn [data]
    [:div.container grid-styles
     [:div.row.d-flex.justify-content-around
      [:div.col-4.border-top.border-right.border-left.rounded.text-center.block
       {:class (if (= :meteo (:current-tab data))
                 "tab-active"
                 "tab pointer")
        :on-click (fn [e]
                    (rf/dispatch [::model/switch-tab :meteo]))}
       "Meteorological data"]
      [:div.col-4.border-top.border-right.border-left.rounded.text-center.block
       {:class (if (= :sensor (:current-tab data))
                 "tab-active"
                 "tab pointer")
        :on-click (fn [e]
                    (rf/dispatch [::model/switch-tab :sensor]))}
       "Sensor data"]]
     [:div.border.rounded-top.p-3.block
      [:div.container
       [:div.pointer.btn.btn-primary.p-1.mb-2 {:on-click #(rf/dispatch
                                                           [::modal/modal {:persistent true
                                                                           :title "Create new record"
                                                                           :close (rf/dispatch [::form/deinit])
                                                                           :body [modal-form (:current-tab data)]}])}
        [:i.fas.fa-plus {:style {:font-size "14px"}}]
        [:span.font-weight-bold.ml-1 "Add new record"]]
       [:div.row.border-top.border-bottom.rounded-top
        [:div.col-5.p-2.border-left
         [:p.font-weight-bold.text-center "DateTime"]]
        [:div.text-center.col-3.p-2
         [:p.font-weight-bold.text-center (if (= :meteo (:current-tab data))
                                            "Wind direction"
                                            "Chemical")]]
        [:div.text-center.col-2.p-2
         [:p.font-weight-bold.text-center (if (= :meteo (:current-tab data))
                                            "Wind speed"
                                            "Monitor")]]
        [:div.text-center.col-2.p-2.border-right
         [:p.font-weight-bold.text-center (if (= :meteo (:current-tab data))
                                            "Elevation"
                                            "Reading")]]]
       (for [el (:data data)]
         [:div.grid-record.row
          [:div.col-5.p-2.border-left.border-bottom
           [:p.text-center (:date_ts el)]]
          [:div.col-3.p-2.border-bottom
           [:p.text-center (if (= :meteo (:current-tab data))
                             (:direction el)
                             (:chemical el))]]
          [:div.col-2.p-2.border-bottom
           [:p.text-center (if (= :meteo (:current-tab data))
                             (:speed el)
                             (:monitor el))]]
          [:div.col-2.p-2.border-right.border-bottom.position-relative
           [:i.far.fa-trash-alt.position-absolute.float-right.pointer {:style {:right 0
                                                                               :top 0
                                                                               :color "red"
                                                                               :margin "5px"
                                                                               :font-size "15px"}
                                                                       :on-click (fn [e]
                                                                                   (rf/dispatch [::model/delete-record
                                                                                                 {:id (:id el)
                                                                                                  :type :meteorological-data}]))}]
           [:p.text-center (if (= :meteo (:current-tab data))
                             (:elevation el)
                             (some-> (:reading el)
                                     (.substring 0 7)))]]])
       [:nav.mt-3
        [:ul.pagination
         [:li.page-item
          [:a.page-link {:href "#" :tabindex -1} "Previous"]]
         [:li.page-item
          [:a.page-link {:href "#"} 1]]]]]]]))

(pages/reg-subs-page
 model/index
 (fn [data]
   [meteo-data-grid data]))
