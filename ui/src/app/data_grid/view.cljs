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
    {:background-color "white!important"
     :height "26px!important"
     :border-bottom "none!important"
     :border-radius ".25rem .25rem 0 0 !important"}]
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
       (:form-path i) (:path i)]])])

(defn modal-form [current-tab]
  [:<>
   [form-items (cond
                 (= :meteo current-tab)
                 [{:label "Wind direction"
                   :type :text
                   :form-path form/form-path-meteo
                   :path [:wind_direction]}
                  {:label "Wind speed"
                   :type :text
                   :form-path form/form-path-meteo
                   :path [:wind_speed]}
                  {:label "Elevation"
                   :type :text
                   :form-path form/form-path-meteo
                   :path [:elevation]}
                  {:label "Date"
                   :type :date
                   :form-path form/form-path-meteo
                   :path [:date_ts]}]

                 (= :sensor current-tab)
                 [{:label "Chemical"
                   :type :text
                   :form-path form/form-path-sensor
                   :path [:chemical]}
                  {:label "Monitor"
                   :type :text
                   :form-path form/form-path-sensor
                   :path [:monitor]}
                  {:label "Reading"
                   :type :text
                   :form-path form/form-path-sensor
                   :path [:reading]}
                  {:label "Date"
                   :type :date
                   :form-path form/form-path-sensor
                   :path [:date_ts]}]

                 (= :factory current-tab)
                 [{:label "Factory name"
                   :type :text
                   :form-path form/form-path-factory
                   :path [:factory_name]}
                  {:label "Longitude"
                   :type :text
                   :form-path form/form-path-factory
                   :path [:longitude]}
                  {:label "Latitude"
                   :type :text
                   :form-path form/form-path-factory
                   :path [:latitude]}
                  {:label "Description"
                   :type :text
                   :form-path form/form-path-factory
                   :path [:description]}]

                 (= :monitor current-tab)
                 [{:label "Identification number"
                   :type :text
                   :form-path form/form-path-monitor
                   :path [:id]}
                  {:label "Longitude"
                   :type :text
                   :form-path form/form-path-monitor
                   :path [:longitude]}
                  {:label "Latitude"
                   :type :text
                   :form-path form/form-path-monitor
                   :path [:latitude]}])]
   [:div.btn.btn-primary.mt-5.pointer {:on-click #(rf/dispatch [::model/send-data current-tab])}
    "Create record"]])
(defn modal-file [current-tab]
  [:<>
   [:div.row.m-4
    [:input {:type "file"
             :onChange (fn [e]
                         (let [file (aget (.-files (.querySelector js/document "input[type=file]")) 0)]
                           (rf/dispatch [::model/add-file {:file file
                                                           :current-tab current-tab}])))}]
    [:div.form-check
     [:input.form-check-input {:type "checkbox"
                               :on-click #(rf/dispatch [::model/overwrite-data])}]
     [:label.form-check-label
      "Overwrite data"]]
    #_[:label.btn.btn-primary.btn-file.pointer.mr-4
       "Choose file"
       [:input {:type "file"
                :style {:display "none"}}]]]
   [:div.btn.btn-primary.ml-4.pointer {:on-click #(rf/dispatch [::model/upload-file [::modal/close-modal]])}
    "Upload"]])

(defn meteo-data-grid [data]
  (fn [data]
    [:div.container grid-styles
     [:div.row.position-relative
      {:style {:height "25px"}}
      [:div.col-2.border-top.border-right.border-left.border-bottom.rounded.text-center.bg-light
       {:class (if (= :meteo (:current-tab data))
                 "tab-active"
                 "tab pointer")
        :style {:margin-left "8.5%"}
        :on-click (fn [e]
                    (rf/dispatch [::model/switch-tab :meteo]))}
       "Meteorological data"]
      [:div.col-2.border-top.border-right.border-left.border-bottom.rounded.text-center.bg-light
       {:class (if (= :sensor (:current-tab data))
                 "tab-active"
                 "tab pointer")
        :style {:margin-left "5%"}
        :on-click (fn [e]
                    (rf/dispatch [::model/switch-tab :sensor]))}
       "Sensor data"]
      [:div.col-2.border-top.border-right.border-left.border-bottom.rounded.text-center.bg-light
       {:class (if (= :factory (:current-tab data))
                 "tab-active"
                 "tab pointer")
        :style {:margin-left "5%"}
        :on-click (fn [e]
                    (rf/dispatch [::model/switch-tab :factory]))}
       "Factories"]
      [:div.col-2.border-top.border-right.border-left.border-bottom.rounded.text-center.bg-light
       {:class (if (= :monitor (:current-tab data))
                 "tab-active"
                 "tab pointer")
        :style {:margin-left "5%"}
        :on-click (fn [e]
                    (rf/dispatch [::model/switch-tab :monitor]))}
       "Monitors"]]
     [:div.border.rounded-top.p-3.block
      (when (#{:sensor :meteo} (:current-tab data))
        [:div.pb-3.d-flex.align-items-baseline
         [inputs/text-input [:form :search]]
         [:button.btn.btn-primary.ml-1 {:on-click #(rf/dispatch [::model/search])} "Search"]])
      [:div.container
       (if (-> data :file-upload :uploading?)
         [:button.btn.btn-primary.p-1.mb-2.mr-3 {:type "button"
                                                 :disabled true}
          [:span.spinner-border.spinner-border-sm.mr-2 {:role "status"
                                                        :aria-hidden "true"}]
          "Uploading"]
         [:div.pointer.btn.btn-primary.p-1.mb-2.mr-3 {:on-click #(rf/dispatch
                                                                  [::modal/modal {:persistent true
                                                                                  :title "Upload from file"
                                                                                  :close (rf/dispatch [::model/init-file-modal])
                                                                                  :body [modal-file (:current-tab data)]}])}
          [:i.fas.fa-file {:style {:font-size "14px"}}]
          [:span.font-weight-bold.ml-1 "Upload from file"]])
       [:div.pointer.btn.btn-primary.p-1.mb-2 {:on-click #(rf/dispatch
                                                           [::modal/modal {:persistent true
                                                                           :title "Create new record"
                                                                           :close (rf/dispatch [::form/deinit])
                                                                           :body [modal-form (:current-tab data)]}])}
        [:i.fas.fa-plus {:style {:font-size "14px"}}]
        [:span.font-weight-bold.ml-1 "Add new record"]]
       [:div.row.border-top.border-bottom.rounded-top
        [:div.col-5.p-2.border-left
         [:p.font-weight-bold.text-center (cond
                                            (#{:meteo :sensor} (:current-tab data))
                                            "DateTime"

                                            (= :factory (:current-tab data))
                                            "Factory name"

                                            :else
                                            "Identification number")]]
        [:div.text-center.col-3.p-2
         [:p.font-weight-bold.text-center (cond
                                            (= :meteo (:current-tab data))
                                            "Wind direction"

                                            (= :sensor (:current-tab data))
                                            "Chemical"

                                            :else
                                            "Latitude"

                                            )]]
        [:div.text-center.col-2.p-2
         [:p.font-weight-bold.text-center (cond
                                            (= :meteo (:current-tab data))
                                            "Wind speed"

                                            (= :sensor (:current-tab data))
                                            "Monitor"

                                            :else
                                            "Longitude"
                                            )]]
        [:div.text-center.col-2.p-2.border-right
         [:p.font-weight-bold.text-center (cond
                                            (= :meteo (:current-tab data))
                                            "Elevation"

                                            (= :sensor (:current-tab data))
                                            "Reading"

                                            (= :factory (:current-tab data))
                                            "Description")]]]
       (for [el (:data data)]
         [:div.grid-record.row
          [:div.col-5.p-2.border-left.border-bottom
           [:p.text-center (or (:date_ts el) (:factory_name el) (:id el))]]
          [:div.col-3.p-2.border-bottom
           [:p.text-center (cond
                             (= :meteo (:current-tab data))
                             (:direction el)

                             (= :sensor (:current-tab data))
                             (:chemical el)

                             (#{:monitor :factory} (:current-tab data))
                             (:latitude el)
                             )]]
          [:div.col-2.p-2.border-bottom
           [:p.text-center (cond
                             (= :meteo (:current-tab data))
                             (:speed el)

                             (= :sensor (:current-tab data))
                             (:monitor el)

                             (#{:monitor :factory} (:current-tab data))
                             (:longitude el))]]
          [:div.col-2.p-2.border-right.border-bottom.position-relative
           [:i.far.fa-trash-alt.position-absolute.float-right.pointer {:style {:right 0
                                                                               :top 0
                                                                               :color "red"
                                                                               :margin "5px"
                                                                               :font-size "15px"}
                                                                       :on-click (fn [e]
                                                                                   (rf/dispatch [::model/delete-record
                                                                                                 {:id (:id el)}]))}]
           [:p.text-center (cond
                             (= :meteo (:current-tab data))
                             (:elevation el)

                             (= :sensor (:current-tab data))
                             (some-> (:reading el)
                                     (.substring 0 7))

                             (= :factory (:current-tab data))
                             (:description el))]]])
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
