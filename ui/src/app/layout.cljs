(ns app.layout
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [app.styles :as styles]
            [app.modal  :as modal]
            [zframes.flash :as flash]))

(def menu
  [{:title "Data"
    :uri   "#/"}
   {:title "Map"
    :uri   "#/map"}
   {:title "Analytics"
    :uri   "#/analytics?type=chemicals"}])

(def layout-style
  (styles/style
   [:.flashes {:position "fixed" :top "60px" :right "20px" :max-width "500px" :z-index 999}
    [:ul {:padding-left "20px"}]]))

(defn layout [page]
  [:div.h-100 layout-style
   [:nav.navbar.sticky-top.navbar-dark.navbar-expand-lg.bg-primary.mb-20
    {:style {:margin-bottom "20px"}}
    [:a.navbar-brand "VAST"]
    [:div
     [:ul.navbar-nav
      (for [item menu]
        [:li.nav-item
         [:a.nav-link {:href (:uri item)}
          (:title item)]])]]]
   [flash/flashes]
   [:div
    [:div#content
     [modal/modal]
     page]]])
