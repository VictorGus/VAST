(ns app.data-grid.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [app.pages :as pages]
            [app.styles :as styles]
            [app.data-grid.model :as model]))

(def grid-styles
  (styles/style
   [:.grid-record:hover
    {:background-color "#e6f2ff"}]))

(defn meteo-data-grid [data]
  (fn [data]
    [:div.w-50.container grid-styles
     [:div.row.border-top.border-bottom
      [:div.col-5.p-2.border-left
       [:p.font-weight-bold.text-center "DateTime"]]
      [:div.text-center.col-3.p-2
       [:p.font-weight-bold.text-center"Wind direction"]]
      [:div.text-center.col-2.p-2
       [:p.font-weight-bold.text-center "Wind speed"]]
      [:div.text-center.col-2.p-2.border-right
       [:p.font-weight-bold.text-center "Elevation"]]]
     (for [el data]
       [:div.grid-record.row
        [:div.col-5.p-2.border-left.border-bottom
         [:p.text-center (:date_ts el)]]
        [:div.col-3.p-2.border-bottom
         [:p.text-center (:direction el)]]
        [:div.col-2.p-2.border-bottom
         [:p.text-center (:speed el)]]
        [:div.col-2.p-2.border-right.border-bottom.position-relative
         [:i.far.fa-trash-alt.position-absolute.float-right {:style {:right 0
                                                                     :top 0
                                                                     :cursor "pointer"
                                                                     :color "red"
                                                                     :margin "5px"
                                                                     :font-size "11px"}
                                                             :on-click (fn [e]
                                                                         (rf/dispatch [::model/delete-record
                                                                                       {:id (:id el)
                                                                                        :type :meteorological-data}]))}]
         [:p.text-center (:elevation el)]]])]))

(pages/reg-subs-page
 model/index
 (fn [data]
   [meteo-data-grid data]))
