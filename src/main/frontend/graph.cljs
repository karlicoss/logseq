(ns frontend.graph
  (:require [frontend.handler.route :as route-handler]
            [clojure.string :as string]
            [cljs-bean.core :as bean]
            [goog.object :as gobj]
            [frontend.state :as state]
            [frontend.db :as db]
            ["three-spritetext" :as SpriteText]
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

(defn- dot-text-mode
  [node ctx global-scale dark?]
  (let [hide-text? (< global-scale 0.45)
        label (gobj/get node "id")
        val (gobj/get node "val")
        val (if (zero? val) 1 val)
        font-size (min
                   10
                   (* (/ 15 global-scale) (js/Math.cbrt val)))
        arc-radius (/ 3 global-scale)
        _ (set! (.-font ctx)
                (str font-size "px Inter"))
        text-width (gobj/get (.measureText ctx label) "width")
        x (gobj/get node "x")
        y (gobj/get node "y")
        color (gobj/get node "color")]
    (set! (.-filltextAlign ctx) "center")
    (set! (.-textBaseLine ctx) "middle")
    (set! (.-fillStyle ctx) color)
    (when-not hide-text?
      (.fillText ctx label
                 (- x (/ text-width 2))
                 (- y (/ 9 global-scale))))

    (.beginPath ctx)
    (.arc ctx x y (if (zero? val)
                    arc-radius
                    (* arc-radius (js/Math.sqrt (js/Math.sqrt val)))) 0 (* 2 js/Math.PI) false)
    (set! (.-fillStyle ctx)
          (if (contains? @highlight-nodes label)
            (if dark? "#A3BFFA" "#4C51BF")
            (if dark? "#999" "#666")))
    (.fill ctx)))


(defn- style-3d-node
  [node]
  (let [label (gobj/get node "id")
        val (gobj/get node "val")
        val (max val 2.0)
        textcolor (gobj/get node "color")
        height (js/Math.min
                30
                (js/Math.max
                 5
                 (js/Math.exp val)))
        ;; shit, it overlaps the node and ends up pretty ugly... why does it have to be so hard
        res (new SpriteText label)]
    (set! (.-color           res) textcolor)
    (set! (.-textHeight      res) height)
    res))

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
  [graph dark? option]
  (let [nodes-count (count (:nodes graph))
        graph-data (build-graph-data graph)]
    (merge
     {:graphData (bean/->js graph-data)
      ;; :nodeRelSize node-r
      :linkWidth 1
      :backgroundColor "white"
      :linkDirectionalParticles 2
      :linkDirectionalParticleWidth  1
      :onNodeHover on-node-hover
      :nodeLabel "id"
      :linkColor (fn [] (if dark? "rgba(255,255,255,0.2)" "rgba(0,0,0,0.1)"))
      ;; :cooldownTicks 100
      ;; :onEngineStop (fn []
      ;;                 (when-let [ref (:ref-atom option)]
      ;;                   (.zoomToFit @ref 400)))
      ; vvvv this is the most important thing here!
      :nodeThreeObject style-3d-node}
     option)))
