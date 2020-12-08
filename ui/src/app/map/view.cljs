(ns app.map.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [app.pages :as pages]
            [app.styles :as styles]
            [app.map.model :as model])
  (:require-macros [hiccup.core :as hc]))


(defn map-page [data]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [usa-map (js/Datamap. (clj->js {:element (.getElementById js/document "container")
                                           :scope "usa"
                                           :geographyConfig {:borderColor "black"
                                                             :borderWidth "1"
                                                             :popupOnHover true
                                                             :hideHawaiiAndAlaska true
                                                             :highlightOnHover true}
                                           :fills {:defaultFill "white"
                                                   "FACTORY" "#ff7f0e"
                                                   "MONITOR" "#3f8c07"}}))
            markers (clj->js [{:latitude  40.806740
                               :fillKey "FACTORY"
                               :name "Factory 1"
                               :radius 10
                               :fillOpacity 2.0
                               :longitude -94.671850}
                              {:latitude  42.806740
                               :fillKey "FACTORY"
                               :name "Factory 2"
                               :radius 10
                               :fillOpacity 2.0
                               :longitude -104.671850}
                              {:latitude  37.806740
                               :fillKey "FACTORY"
                               :name "Factory 3"
                               :radius 10
                               :fillOpacity 2.0
                               :longitude -96.671850}
                              {:latitude  38.806740
                               :fillKey "FACTORY"
                               :name "Factory 4"
                               :radius 10
                               :fillOpacity 2.0
                               :longitude -101.671850}
                              {:latitude  35.806740
                               :fillKey "MONITOR"
                               :name "Factory 4"
                               :radius 6
                               :fillOpacity 2.0
                               :longitude -99.671850}
                              {:latitude  36.806740
                               :fillKey "MONITOR"
                               :name "Factory 4"
                               :radius 6
                               :fillOpacity 2.0
                               :longitude -94.671850}
                              {:latitude  36.806740
                               :fillKey "MONITOR"
                               :name "Factory 4"
                               :radius 6
                               :fillOpacity 2.0
                               :longitude -103.671850}
                              {:latitude  39.806740
                               :fillKey "MONITOR"
                               :name "Factory 4"
                               :radius 6
                               :fillOpacity 2.0
                               :longitude -92.671850}
                              {:latitude  42.806740
                               :fillKey "MONITOR"
                               :name "Factory 4"
                               :radius 6
                               :fillOpacity 2.0
                               :longitude -91.671850}
                              ])]
        (.bubbles usa-map markers (clj->js {:popupTemplate (fn [geo data]
                                                             (let [data (js->clj data :keywordize-keys true)]
                                                               (println data)
                                                               (str "<div class=hoverinfo>"
                                                                    "<span class=font-weight-bold>" (:name data) "</span>"
                                                                    (when-let [desc (:description data)] (str "<br/>" "Description: " desc))
                                                                    "<br/>" (str "Longitude: " (:longitude data))
                                                                    "<br/>" (str "Latitude: "  (:latitude data))
                                                                    "</div>")))}))))
    :reagent-render
    (fn [data]
      [:div.container
       [:div#container {:style {:min-height "500px"}}]])}))

(pages/reg-subs-page
 model/index
 (fn [data]
   [map-page data]))

