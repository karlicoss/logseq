(ns frontend.extensions.graph-2d
  (:require [rum.core :as rum]
            [frontend.loader :as loader]
            [frontend.config :as config]
            [goog.dom :as gdom]
            [goog.object :as gobj]
            [frontend.rum :as r]))

;; TODO: extracted to a rum mixin
(defn loaded? []
  js/window.ForceGraph)

(defonce graph-component
  (atom nil))

(defonce *loading? (atom true))

(rum/defc graph < rum/reactive
  {:init (fn [state]
           (if @graph-component
             (reset! *loading? false)
             (do
               ;; ok, at least I didn't have to manually remove the component thing?
               ;; yarn add react-force-graph-3d
               ;; cp node_modules/react-force-graph-2d/dist/react-force-graph-2d.js static/js/
               ;; (for some reason doesn't load directly from node_modules??)
               (loader/load
                (config/asset-uri "/static/js/react-force-graph-2d.js")
                (fn []
                  (reset! graph-component
                          ;; NOTE: need to hard refresh page (Ctrl-F5)
                          (r/adapt-class js/window.ForceGraph2D))
                  (reset! *loading? false)))))
           state)}
  [opts]
  (let [loading? (rum/react *loading?)]
    (when @graph-component
      (@graph-component
       opts))))
