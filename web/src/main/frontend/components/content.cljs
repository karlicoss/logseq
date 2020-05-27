(ns frontend.components.content
  (:require [rum.core :as rum]
            [frontend.format :as format]
            [frontend.handler :as handler]
            [frontend.util :as util]
            [frontend.state :as state]
            [frontend.mixins :as mixins]
            [frontend.ui :as ui]
            [frontend.expand :as expand]
            [frontend.config :as config]
            [goog.dom :as gdom]
            [goog.object :as gobj]
            [dommy.core :as d]
            [clojure.string :as string]
            [frontend.components.editor :as editor]))

(defn lazy-load-js
  [state]
  (when-let [format (:format (last (:rum/args state)))]
    (let [loader? (contains? config/html-render-formats format)]
      (when loader?
        (when-not (format/loaded? format)
          (handler/lazy-load format))))))

(defn highlight-block-if-fragment
  []
  (when-let [fragment (util/get-fragment)]
    (when-let [element (gdom/getElement fragment)]
      (d/add-class! element "highlight-area")
      ;; (js/setTimeout #(d/remove-class! element "highlight-area")
      ;;                2000)
      )))

;; TODO: content could be changed
;; Also, keyboard bindings should only be activated after
;; headings were already selected.
(defn- cut-headings-and-clear-selections!
  [_]
  (handler/cut-selection-headings)
  (handler/clear-selection!))

(rum/defc hidden-selection < rum/reactive
  (mixins/keyboard-mixin "ctrl+c"
                         (fn [_]
                           (handler/copy-selection-headings)
                           (handler/clear-selection!)))
  (mixins/keyboard-mixin "ctrl+x" cut-headings-and-clear-selections!)
  (mixins/keyboard-mixin "backspace" cut-headings-and-clear-selections!)
  (mixins/keyboard-mixin "delete" cut-headings-and-clear-selections!)
  []
  [:div#selection.hidden])

(rum/defc hiccup-content < rum/reactive
  (mixins/event-mixin
   (fn [state]
     (mixins/listen state js/window "mouseup"
                    (fn [e]
                      (when-let [headings (seq (util/get-selected-nodes "ls-heading-parent"))]
                        (let [headings (remove #(d/has-class? % "dummy") headings)]
                          (doseq [heading headings]
                            (d/add-class! heading "selected noselect"))
                          ;; TODO: We delay this so the following "click" event won't clear the selections.
                          ;; Needs more thinking.
                          (js/setTimeout #(state/set-selection-headings! headings)
                                         200)))))

     (mixins/listen state js/window "click"
                    (fn [e]
                      ;; hide context menu
                      (state/hide-custom-context-menu!)

                      ;; enable scroll
                      (let [main (d/by-id "main-content")]
                        (d/remove-class! main "overflow-hidden")
                        (d/add-class! main "overflow-y-auto")
                        )

                      (handler/clear-selection!)))

     (mixins/listen state js/window "contextmenu"
                    (fn [e]
                      (when (state/in-selection-mode?)
                        (util/stop e)
                        (let [client-x (gobj/get e "clientX")
                              client-y (gobj/get e "clientY")]
                          (let [main (d/by-id "main-content")]
                            ;; disable scroll
                            (d/remove-class! main "overflow-y-auto")
                            (d/add-class! main "overflow-hidden"))

                          (state/show-custom-context-menu!)
                          (when-let [context-menu (d/by-id "custom-context-menu")]
                            (d/set-style! context-menu
                                          :left (str client-x "px")
                                          :top (str client-y "px")))))))))
  [id {:keys [hiccup] :as option}]
  (let [in-selection-mode? (state/sub :selection/mode)]
    [:div {:id id}
     hiccup
     (when in-selection-mode?
       (hidden-selection))]))

(rum/defc non-hiccup-content < rum/reactive
  [id content on-click on-hide config format]
  (let [edit? (= (state/sub-edit-input-id) id)
        loading (state/sub :format/loading)]
    (if edit?
      (editor/box (string/trim content)
                  {:on-hide on-hide
                   :format format} id)
      (let [format (format/normalize format)
            loading? (get loading format)
            markup? (contains? config/html-render-formats format)
            on-click (fn [e]
                       (when-not (util/link? (gobj/get e "target"))
                         (util/stop e)
                         (handler/reset-cursor-range! (gdom/getElement (str id)))
                         (state/set-edit-input-id! id)
                         (when on-click
                           (on-click e))))]
        (cond
          (and markup? loading?)
          [:div "loading ..."]

          markup?
          (let [html (format/to-html content format config)]
            (if (string/blank? html)
              [:div.cursor.content
               {:id id
                :on-click on-click}
               [:div.text-gray-500.cursor "Click to edit"]]
              [:div.cursor.content
               {:id id
                :on-click on-click
                :dangerouslySetInnerHTML {:__html html}}]))

          :else                       ; other text formats
          [:pre.cursor.content
           {:id id
            :on-click on-click}
           (if (string/blank? content)
             [:div.text-gray-500.cursor "Click to edit"]
             content)])))))

;; TODO: lazy load highlight.js
(rum/defcs content <
  (mixins/event-mixin
   (fn [state]
     (mixins/listen state js/window "keyup"
                    (fn [e]
                      ;; t
                      (when (and
                             ;; not input, t
                             (not (state/get-edit-input-id))
                             (= 84 (.-keyCode e)))
                        (let [id (first (:rum/args state))]
                          (expand/toggle-all! id)))))))
  {:will-mount (fn [state]
                 (lazy-load-js state)
                 state)
   :did-mount (fn [state]
                (highlight-block-if-fragment)
                (util/code-highlight!)
                (handler/render-local-images!)
                state)
   :did-update (fn [state]
                 (highlight-block-if-fragment)
                 (util/code-highlight!)
                 (handler/render-local-images!)
                 (lazy-load-js state)
                 state)}
  [state id {:keys [format
                    config
                    hiccup
                    content
                    on-click
                    on-hide]
             :as option}]
  (if hiccup
    (hiccup-content id option)
    (let [format (format/normalize format)]
      (non-hiccup-content id content on-click on-hide config format))))