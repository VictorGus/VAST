(ns app.map.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [app.pages :as pages]
            [app.styles :as styles]
            [app.map.model :as model])
  (:require-macros [hiccup.core :as hc]))

(defn normalize-coords [hm]
  (-> hm
      (assoc :latitude  (js/parseFloat (:latitude  hm)))
      (assoc :longitude (js/parseFloat (:longitude hm)))))

(defn map-page [data]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [factory-bubble {:radius 10
                            :fillOpacity 2.0
                            :fillKey "FACTORY"}
            monitor-bubble {:radius 6
                            :fillOpacity 2.0
                            :fillKey "MONITOR"}
            usa-map (js/Datamap. (clj->js {:element (.getElementById js/document "container")
                                           :scope "usa"
                                           :geographyConfig {:borderColor "black"
                                                             :borderWidth "1"
                                                             :popupOnHover true
                                                             :hideHawaiiAndAlaska true
                                                             :highlightOnHover true}
                                           :fills {:defaultFill "white"
                                                   "FACTORY" "#ff7f0e"
                                                   "MONITOR" "#3f8c07"}}))
            markers (clj->js (vec
                              (concat
                               (map
                                (comp (partial merge monitor-bubble) normalize-coords)
                                (:monitors @model/mini-db))
                               (map
                                (comp (partial merge factory-bubble) normalize-coords)
                                (:factories @model/mini-db)))))]
        (.bubbles usa-map markers (clj->js {:popupTemplate (fn [geo data]
                                                             (let [data (js->clj data :keywordize-keys true)]
                                                               (str "<div class=hoverinfo>"
                                                                    "<span class=font-weight-bold>" (or (:factory_name data) (:id data)) "</span>"
                                                                    (when-let [desc (:description data)] (str "<br/>" "Description: " desc))
                                                                    "<br/>" (str "Longitude: " (:longitude data))
                                                                    "<br/>" (str "Latitude: "  (:latitude data))
                                                                    "</div>")))}))))
    :reagent-render
    (fn [data]
      [:div.container
       [:div#container {:style {:min-height "500px"}}]
       [:span
        [:i.fas.fa-circle {:style {:color "#ff7f0e"}}]
        " - factories"]
       [:span.ml-5
        [:i.fas.fa-circle {:style {:color "#3f8c07"
                                   :font-size "10px"}}]
        " - monitors"]
       ])}))

(pages/reg-subs-page
 model/index
 (fn [data]
   [map-page data]))

