(ns frontend.graph
  (:require [frontend.handler.route :as route-handler]
            [clojure.string :as string]
            [cljs-bean.core :as bean]
            [goog.object :as gobj]
            [clojure.pprint :refer [pprint]]
            [frontend.state :as state]
            [frontend.db :as db]
            [cljs-bean.core :as bean]))

;; translated from https://github.com/vasturiano/react-force-graph/blob/master/example/highlight/index.html
(defonce graph-mode (atom :dot-text))
(defonce highlight-nodes (atom #{}))
(defonce highlight-links (atom #{}))
(defonce hover-node (atom nil))
(defonce node-r 8)

(defn- clear-highlights!
  []
  (reset! highlight-nodes #{})
  (reset! highlight-links #{}))

(defn- highlight-node!
  [node]
  (swap! highlight-nodes conj node))

(defn- highlight-link!
  [link]
  (swap! highlight-links conj (bean/->clj link)))

(defn- on-node-hover
  [node]
  (clear-highlights!)
  (when node
    (highlight-node! (gobj/get node "id"))
    (doseq [neighbor (array-seq (gobj/get node "neighbors"))]
      (highlight-node! neighbor))
    (doseq [link (array-seq (gobj/get node "links"))]
      (highlight-link! link)))
  (reset! hover-node (gobj/get node "id")))

(defn- on-link-hover
  [link]
  (clear-highlights!)
  (when link
    (highlight-link! link)
    (highlight-node! (gobj/get link "source"))
    (highlight-node! (gobj/get link "target"))))

(defonce static-num (js/Math.pow 2 24))
(defn get-color
  [n]
  (str "#" (-> (mod (* n 1234567)
                    static-num)
               (.toString 16)
               (.padStart 6 "0"))))

(defn- dot-mode
  [node ctx global-scale dark?]
  (let [label (gobj/get node "id")
        val (gobj/get node "val")
        font-size (/ 15 global-scale)
        arc-radius (/ 3 global-scale)
        x (gobj/get node "x")
        y (gobj/get node "y")
        color (gobj/get node "color")]
    (set! (.-fillStyle ctx) color)
    (.beginPath ctx)
    (.arc ctx x y (if (zero? val)
                    arc-radius
                    (* arc-radius (js/Math.sqrt (js/Math.sqrt val)))) 0 (* 2 js/Math.PI) false)
    (set! (.-fillStyle ctx) (if dark? "#aaa" "#222"))
    (.fill ctx)))

(defn- dot-text-mode
  [node ctx global-scale dark?]
  (let [label (gobj/get node "id")
        val (gobj/get node "val")
        val (if (zero? val) 1 val)
        highlighted (contains? @highlight-nodes label)
        font-size (min
                   10
                   (* (/ 15 global-scale) (js/Math.cbrt val)))
        arc-radius (/ 3 global-scale)
        _ (set! (.-font ctx)
                (str font-size "px Inter"))
        text-width (gobj/get (.measureText ctx label) "width")
        x (gobj/get node "x")
        y (gobj/get node "y")
        color (if highlighted "#a00" (gobj/get node "color"))]
    (set! (.-filltextAlign ctx) "center")
    (set! (.-textBaseLine ctx) "middle")
    (set! (.-fillStyle ctx) color)
    (.fillText ctx label
               (- x (/ text-width 2))
               (- y (/ 9 global-scale)))


    ;; ok, this sort of displays 'bounding boxes' for hover tooltips?
    ;; (set! (.-fillStyle ctx) "#66666611")
    ;; (.arc ctx x y val 0 (* 2 js/Math.PI) false)
    ;; (.fill ctx)


    (.beginPath ctx)
    (.arc ctx x y (if (zero? val)
                    arc-radius
                    (* arc-radius (js/Math.sqrt (js/Math.sqrt val)))) 0 (* 2 js/Math.PI) false)
    (set! (.-fillStyle ctx)
          (if highlighted
            (if dark? "#A3BFFA" "#4C51BF")
            (if dark? "#999" "#666")))
    (.fill ctx)))

(defn build-graph-data
  [{:keys [links nodes]}]
  (let [nodes (mapv
               (fn [node]
                 (let [links (filter (fn [{:keys [source target]}]
                                       (let [node (:id node)]
                                         (or (= source node) (= target node)))) links)]
                   (assoc node
                          :neighbors (vec
                                      (distinct
                                       (->>
                                        (concat
                                         (mapv :source links)
                                         (mapv :target links))
                                        (remove #(= (:id node) %)))))
                          :links (vec links))))
               nodes)]
    {:links links
     :nodes nodes}))

(defn- build-graph-opts
  [graph dark? dot-mode? option]
  (let [nodes-count (count (:nodes graph))
        graph-data (build-graph-data graph)]
    (merge
     {:graphData (bean/->js graph-data)
      ;; :nodeRelSize node-r
      :linkWidth 1
      ;; :linkWidth 5
      ;; :linkWidth (fn [link]
      ;;              (let [link {:source (gobj/get link "source")
      ;;                          :target (gobj/get link "target")}]
      ;;                    ;; _ (pprint link)]
      ;;                (if (contains? @highlight-links link) 5 1)))
      :linkDirectionalParticles 2
      :linkDirectionalParticleWidth (fn [link]
                                      (let [link {:source (-> (gobj/get link "source")
                                                              (gobj/get "id"))
                                                  :target (-> (gobj/get link "target")
                                                              (gobj/get "id"))}]
                                        (if (contains? @highlight-links link) 4 0)))
      ;; todo wider?

      ;; ugh. ngraph doesn't work??
      ;; :forceEngine "d3"
      ;; :ngraphPhysics {:springLength 100000 :springCoefficient 0}

      ;; :width 3000
      ;; :zoom {:number 100 :ms 20000}
      ;; :cameraPosition [1000, 1000, 1000]
      ;; hmm, with 0.5 value it just freezes? default is 0
      ;; basically the smaller it is, the later it freezes?
      ;; :d3AlphaMin 0.00001
      ;; :d3AlphaDecay 0.01
      ;; :cooldownTime 2000

      ;; controls how fast it disperses?
      ;; :d3AlphaDecay 0.00001
      ;;
      ;; As with lowering the alpha decay rate, less velocity decay may converge on a better solution,
      ;; but risks numerical instabilities and oscillation.
      ;;
      ;; if it's 1 it just never moves?
      :d3VelocityDecay 0.2
      ;;
      :onNodeHover on-node-hover

      ;; TODO for fucks sake, it doesn't work properly?
      :linkHoverPrecision 0
      ;; NOTE: ok, seems that nodeVal/val control the 'hover area' of the link

      ;; eh. in dense graphs it ends up obstructing other nodes, etc. think about it later..
      ;; :onLinkHover on-link-hover
      ;; :nodeVal (fn [n] (if (= (gobj/get n "id") "hpi") (do (js/console.error "YEEES" n) 1000) 1))
      ;; :nodeVal "val"
      :nodeLabel "id"
      :linkColor (fn [] (if dark? "rgba(255,255,255,0.2)" "rgba(0,0,0,0.1)"))
      :onZoom (fn [z]
                (let [k (:k (bean/->clj z))]
                  (reset! graph-mode
                          (cond
                            (or dot-mode? (< k 0.4))
                            :dot

                            :else
                            :dot-text))))
      :onNodeClick (fn [node event]
                     (let [page-name (string/lower-case (gobj/get node "id"))]
                       (if (gobj/get event "shiftKey")
                         (let [repo (state/get-current-repo)
                               page (db/entity repo [:page/name page-name])]
                           (state/sidebar-add-block!
                            repo
                            (:db/id page)
                            :page
                            {:page page}))
                         (route-handler/redirect! {:to :page
                                                   :path-params {:name page-name}}))))
      ;; :cooldownTicks 100
      ;; :onEngineStop (fn []
      ;;                 (when-let [ref (:ref-atom option)]
      ;;                   (.zoomToFit @ref 400)))
      :nodeCanvasObject
      (fn [node ^CanvasRenderingContext2D ctx global-scale]
        (case @graph-mode
          :dot-text
          (dot-text-mode node ctx global-scale dark?)
          (dot-mode node ctx global-scale dark?)))}
     option)))
