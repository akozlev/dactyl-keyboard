(ns dactyl-keyboard.manuform
  (:refer-clojure :exclude [use import])
  (:require [scad-clj.scad :refer :all]
            [scad-clj.model :refer :all]
            [dactyl-keyboard.util :refer :all]
            [dactyl-keyboard.common :refer :all]))

(def column-style :standard)

; controls overall height; original=9 with centercol=3; use 16 for centercol=2
;(def keyboard-z-offset 4)


;; settings for column-style == :fixed 
;; the defaults roughly match maltron settings
;;   http://patentimages.storage.googleapis.com/ep0219944a2/imgf0002.png
;; fixed-z overrides the z portion of the column ofsets above.
;; note: this doesn't work quite like i'd hoped.
(def fixed-angles [(deg2rad 10) (deg2rad 10) 0 0 0 (deg2rad -15) (deg2rad -15)])
(def fixed-x [-41.5 -22.5 0 20.3 41.4 65.5 89.6])  ; relative to the middle finger
(def fixed-z [12.1    8.3 0  5   10.7 14.5 17.5])
(def fixed-tenting (deg2rad 0))

;;;;;;;;;;;;;;;;;;;;;;;
;; general variables ;;
;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;
;; placement functions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

; an array of columns from 0 to number of columns.
(defn columns
  "it creates an array for column placement. where 0 being inner index
   finger's column, 1 being index finger's column, 2 middle finger's, and so on."
  [inner ncols]
  (let [init (case inner
               :outie -1
               :normie 0
               :innie 1)]
    (range init ncols)))

(defn inner-columns
  "it creates an array for column placement. where -1 being inner-inner index
   finger's column, 1 being index finger's column, 2 middle finger's, and so on."
  [ncols]
  (range -1 ncols))
(defn rows
  "it creates an array for row placement. where 0 being top-most row, 1 second
   top-most row, and so on."
  [nrows]
  (range 0 nrows))
(defn inner-rows
  "it creates an array for row placement for the inner-most column. where 0 being
   top-most row, 1 second top-most row, and so on."
  [nrows]
  (range 0 (fcornerrow nrows)))

(defn column-x-delta
  [beta]
  (+ -1 (- (* fcolumn-radius (Math/sin beta)))))
(defn column-base-angle
  [beta centercol]
  (* beta (- centercol 2)))


; this is the function that puts the key switch holes
; based on the row and the column.


(defn key-place
  "puts the keys' shape to its place based on it's column and row."
  [c column row shape]
  (apply-key-geometry c
                      translate
                      (fn [angle obj] (rotate angle [1 0 0] obj))
                      (fn [angle obj] (rotate angle [0 1 0] obj))
                      column row shape))
(defn key-holes
  "determines which keys should be generated based on the configuration."
  [c]
  (let [inner               (get c :configuration-inner-column)
        ncols               (get c :configuration-ncols)
        nrows               (get c :configuration-nrows)
        hide-last-pinky?    (get c :configuration-hide-last-pinky?)
        last-row-count      (get c :configuration-last-row-count)
        lastrow             (flastrow nrows)
        lastcol             (flastcol ncols)
        cornerrow           (fcornerrow nrows)
        last-pinky-location (fn [column row]
                              (and (= row lastrow)
                                   (= column lastcol)))
        hide-pinky          (fn [column row]
                              (not (and (= last-row-count :full)
                                        hide-last-pinky?
                                        (last-pinky-location column row))))]
    (apply union
           (for [column (columns inner ncols)
                 row    (rows nrows)
                 :when  (case last-row-count
                          :zero (not= row lastrow)
                          :two (or (.contains [2 3] column)
                                   (not= row lastrow))
                          :full (or (not (.contains [0 1] column)) (not= row lastrow)))
                 :when  (hide-pinky column row)
                 :when  (case inner
                          :outie (not (and (= column -1)
                                           (<= cornerrow row)))
                          true)]
             (->> (single-plate c)
                  (color [1 1 0])
                  (key-place c column row))))))

(defn key-inner-place
  "it generates the placement of the inner column.
   todo: genericisise it."
  [c column row shape]
  (apply-key-geometry c
                      translate
                      (fn [angle obj] (rotate angle [1 0 0] obj))
                      (fn [angle obj] (rotate angle [0 1 0] obj))
                      column row shape))

(defn inner-key-holes [c]
  (let [nrows (get c :configuration-nrows)]
    (apply union (for [row (inner-rows nrows)]
                   (->> (single-plate c)
                        (key-inner-place c -1 row))))))

(defn caps [c]
  (let [inner               (get c :configuration-inner-column)
        last-row-count      (get c :configuration-last-row-count)
        use-wide-pinky?     (get c :configuration-use-wide-pinky?)
        ncols               (get c :configuration-ncols)
        nrows               (get c :configuration-nrows)
        hide-last-pinky?    (get c :configuration-hide-last-pinky?)
        lastrow             (flastrow nrows)
        cornerrow           (fcornerrow nrows)
        lastcol             (flastcol ncols)
        last-pinky-location (fn [column row]
                              (and (= row lastrow)
                                   (= column lastcol)))
        hide-pinky          (fn [column row]
                              (not (and (= last-row-count :full)
                                        hide-last-pinky?
                                        (last-pinky-location column row))))]
    (apply
     union
     (for [column (columns inner ncols)
           row    (rows nrows)
           :when  (case last-row-count
                    :zero (not= row lastrow)
                    :two (or (.contains [2 3] column)
                             (not= row lastrow))
                    :full (or (not (.contains [0 1] column)) (not= row lastrow)))
           :when  (case inner
                    :outie (not (and (= column -1)
                                     (<= cornerrow row)))
                    true)
           :when  (hide-pinky column row)]
       (->> (sa-cap (if (and use-wide-pinky?
                             (= column lastcol)
                             (not= row lastrow))
                      1.5
                      1))
            (key-place c column row))))))

;;;;;;;;;;;;;;;;;;;;
;; web connectors ;;
;;;;;;;;;;;;;;;;;;;;

(defn wide-post-tr [use-wide-pinky?]
  (if use-wide-pinky?
    (translate [(- (/ mount-width  1.2) post-adj) (- (/ mount-height  2) post-adj) 0] web-post)
    web-post-tr))
(defn wide-post-tl [use-wide-pinky?]
  (if use-wide-pinky?
    (translate [(+ (/ mount-width -1.2) post-adj) (- (/ mount-height  2) post-adj) 0] web-post)
    web-post-tl))
(defn wide-post-bl [use-wide-pinky?]
  (if use-wide-pinky?
    (translate [(+ (/ mount-width -1.2) post-adj) (+ (/ mount-height -2) post-adj) 0] web-post)
    web-post-bl))
(defn wide-post-br [use-wide-pinky?]
  (if use-wide-pinky?
    (translate [(- (/ mount-width  1.2) post-adj) (+ (/ mount-height -2) post-adj) 0] web-post)
    web-post-br))

(defn connectors
  "it creates the wall which connects to each keys in the main body based
   on the configuration provided."
  [c]
  (let [inner               (get c :configuration-inner-column)
        init                (case inner
                              :outie -1
                              :normie 0
                              :innie 1)
        last-row-count      (get c :configuration-last-row-count)
        ncols               (get c :configuration-ncols)
        nrows               (get c :configuration-nrows)
        hide-last-pinky?    (get c :configuration-hide-last-pinky?)
        lastrow             (flastrow nrows)
        cornerrow           (fcornerrow nrows)
        middlerow           (fmiddlerow nrows)
        lastcol             (flastcol ncols)
        last-pinky-location (fn [column row]
                              (and (= row lastrow)
                                   (= column lastcol)))
        hide-pinky          (fn [column row]
                              (not (and (= last-row-count :full)
                                        hide-last-pinky?
                                        (last-pinky-location column row))))]
    (union
     (apply
      union
      (if-not (hide-pinky lastcol lastrow)
        (triangle-hulls (key-place c lastcol lastrow web-post-tr)
                        (key-place c lastcol lastrow web-post-tl)
                        (key-place c lastcol lastrow web-post-br)
                        (key-place c lastcol lastrow web-post-bl))
        ())
      (concat
      ;; row connections
       (for [column (range init (dec ncols))
             row    (range 0 (inc lastrow))
             :when  (case last-row-count
                      :zero (or (not= row lastrow)
                                (and (= row cornerrow)
                                     (= column -1)))
                      :two (or (.contains [2] column)
                               (not= row lastrow))
                      :full (not (and (= row lastrow)
                                      (.contains [-1 0 1] column))))]
         (triangle-hulls
          (key-place c (inc column) row web-post-tl)
          (key-place c column row web-post-tr)
          (key-place c (inc column) row web-post-bl)
          (if (not (and (= column -1)
                        (= row cornerrow)))
            (key-place c column row web-post-br)
            ())))

      ;; column connections
       (for [column (columns inner ncols)
             row    (range 0 lastrow)
             :when  (case last-row-count
                      :zero (not= row cornerrow)
                      :two (or (not= row cornerrow))
                      :full (not (and (= row cornerrow)
                                      (.contains [-1 0 1] column))))]
         (triangle-hulls
          (key-place c column row web-post-br)
          (key-place c column row web-post-bl)
          (key-place c column (inc row) web-post-tr)
          (if (not (and (= column -1)
                        (= row middlerow)))
            (key-place c column (inc row) web-post-tl)
            ())))

      ;; diagonal connections
       (for [column (range init (dec ncols))
             row    (range 0 lastrow)
             :when  (case last-row-count
                      :full (not (or (and (= row lastrow)
                                          (.contains [-1 0 1] column))
                                     (and (= row cornerrow)
                                          (.contains [-1 0 1] column))))
                      (or (not= row cornerrow)))]
         (triangle-hulls
          (key-place c column row web-post-br)
          (key-place c column (inc row) web-post-tr)
          (key-place c (inc column) row web-post-bl)
          (key-place c (inc column) (inc row) web-post-tl)))))
     (case last-row-count
       :two (triangle-hulls (key-place c 2 lastrow   web-post-tr)
                            (key-place c 3 cornerrow web-post-bl)
                            (key-place c 3 lastrow   web-post-tl)
                            (key-place c 3 cornerrow web-post-br)
                            (key-place c 3 lastrow   web-post-tr)
                            (key-place c 4 cornerrow web-post-bl)
                            (key-place c 3 lastrow   web-post-br))
       ()))))

;;;;;;;;;;;;
;; thumbs ;;
;;;;;;;;;;;;

; it dictates the location of the thumb cluster.
; the first member of the vector is x axis, second one y axis,
; while the last one is y axis.
; the higher x axis value is, the closer it to the pinky.
; the higher y axis value is, the closer it to the alphas.
; the higher z axis value is, the higher it is.
(defn thumb-cluster-offsets [c]
  (let [x-offset (get c :configuration-thumb-cluster-offset-x)
        y-offset (get c :configuration-thumb-cluster-offset-y)
        z-offset (get c :configuration-thumb-cluster-offset-z)]
    [x-offset y-offset z-offset]))

; this is where the original position of the thumb switches defined.
; each and every thumb keys is derived from this value.
; the value itself is defined from the 'm' key's position in qwerty layout
; and then added by some values, including thumb-offsets above.
(defn thumborigin [c]
  (let [cornerrow (fcornerrow (get c :configuration-nrows))]
    (map + (key-position c 1 cornerrow [(/ mount-width 2) (- (/ mount-height 2)) 0])
         (thumb-cluster-offsets c))))

(defn thumb-tenting [c default-val custom-rotation]
  (let [custom-tenting? (get c :configuration-custom-thumb-tenting?)
        custom-degree (get-in c [custom-rotation])]
    (if custom-tenting? custom-degree (deg2rad default-val))))

(defn thumb-offset [c default]
  (let [custom-offset? (get c :configuration-custom-thumb-offsets?)]
    (if custom-offset? [get c :configuration-custom-] default)))

(defn thumb-tl-place [c shape]
  (let [thumb-count     (get c :configuration-thumb-count)
        x-rotation      (thumb-tenting c 10 :configuration-custom-thumb-tenting-x)
        y-rotation      (thumb-tenting c -23 :configuration-custom-thumb-tenting-y)
        z-rotation      (thumb-tenting c (case thumb-count :three 20 :five 25 10) :configuration-custom-thumb-tenting-z)
        movement        (case thumb-count :five [-35 -16 -2] [-34 -16 -2])]
    (->> shape
      (rotate x-rotation [1 0 0])
      (rotate y-rotation [0 1 0])
      (rotate z-rotation [0 0 1])
      (translate (thumborigin c))
      (translate movement))))

(defn thumb-tr-place [c shape]
  (let [thumb-count (get c :configuration-thumb-count)
        x-rotation (thumb-tenting c (if (= thumb-count :five) 14 10) :configuration-custom-thumb-tenting-x)
        y-rotation (thumb-tenting c (if (= thumb-count :five) -15 -23) :configuration-custom-thumb-tenting-y)
        z-rotation (thumb-tenting c 10 :configuration-custom-thumb-tenting-z)
        custom-offsets? (get c :configuration-custom-thumb-offsets?)
        default-x (if (= thumb-count :five) -15 -12)
        default-y (if (= thumb-count :five) -10 -16)
        default-z (if (= thumb-count :five) 5 3)
        offset (if custom-offsets?
                 [
                  (get c :configuration-thumb-top-right-offset-x)
                  (get c :configuration-thumb-top-right-offset-y)
                  (get c :configuration-thumb-top-right-offset-z)
                 ]
                 [default-x default-y default-z])
        ]
    (->> shape
         (rotate x-rotation [1 0 0])
         (rotate y-rotation [0 1 0])
         (rotate z-rotation [0 0 1])
         (translate (thumborigin c))
         (translate offset))))

(defn thumb-ml-place [c shape]
  (let [thumb-count (get c :configuration-thumb-count)
        x-rotation (thumb-tenting c 0 :configuration-custom-thumb-tenting-x)
        y-rotation (thumb-tenting c -34 :configuration-custom-thumb-tenting-y)
        z-rotation (thumb-tenting c 38 :configuration-custom-thumb-tenting-z)
        movement    (if (= thumb-count :three) [-53 -26 -12] [-52 -23 -12])]
    (->> shape
         (rotate x-rotation [1 0 0])
         (rotate y-rotation [0 1 0])
         (rotate z-rotation [0 0 1])
         (translate (thumborigin c))
         (translate movement))))

(defn thumb-mr-place [c shape]
  (let [thumb-count (get c :configuration-thumb-count)
        x-rotation (thumb-tenting c (if (= thumb-count :five) 10 6) :configuration-custom-thumb-tenting-x)
        y-rotation (thumb-tenting c (if (= thumb-count :five) -23 -34) :configuration-custom-thumb-tenting-y)
        z-rotation (thumb-tenting c (if (= thumb-count :five) 25 28) :configuration-custom-thumb-tenting-z)
        movement    (if (= thumb-count :five) [-23 -34 -6] [-26 -38 -11])]
    (->> shape
         (rotate x-rotation [1 0 0])
         (rotate y-rotation [0 1 0])
         (rotate z-rotation [0 0 1])
         (translate (thumborigin c))
         (translate movement))))

(defn thumb-bl-place [c shape]
  (let [thumb-count (get c :configuration-thumb-count)
        x-rotation (thumb-tenting c (if (= thumb-count :five) 6 -4) :configuration-custom-thumb-tenting-x)
        y-rotation (thumb-tenting c (if (= thumb-count :five) -32 -35) :configuration-custom-thumb-tenting-y)
        z-rotation (thumb-tenting c (if (= thumb-count :five) 35 52) :configuration-custom-thumb-tenting-z)
        movement    (if (= thumb-count :five) [-51 -25 -11.5] [-56.3 -42.3 -23.5])]
    (->> shape
         (rotate x-rotation [1 0 0])
         (rotate y-rotation [0 1 0])
         (rotate z-rotation [0 0 1])
         (translate (thumborigin c))
         (translate movement))))

(defn thumb-br-place [c shape]
  (let [thumb-count (get c :configuration-thumb-count)
        x-rotation (thumb-tenting c (if (= thumb-count :five) 6 -16) :configuration-custom-thumb-tenting-x)
        y-rotation (thumb-tenting c (if (= thumb-count :five) -34 -33) :configuration-custom-thumb-tenting-y)
        z-rotation (thumb-tenting c (if (= thumb-count :five) 35 54) :configuration-custom-thumb-tenting-z)
        movement    (if (= thumb-count :five) [-39 -43 -16] [-35.8 -55.3 -25.3])]
    (->> shape
         (rotate x-rotation [1 0 0])
         (rotate y-rotation [0 1 0])
         (rotate z-rotation [0 0 1])
         (translate (thumborigin c))
         (translate movement))))

(defn thumb-1x-layout [c shape]
  (let [thumb-count (get c :configuration-thumb-count)]
    (case thumb-count
      :two ()
      :three ()
      :four (union (thumb-ml-place c shape)
                   (thumb-mr-place c shape))
      :five (union (thumb-tr-place c shape)
                   (thumb-tl-place c shape)
                   (thumb-mr-place c shape)
                   (thumb-br-place c shape)
                   (thumb-bl-place c shape))
      (union (thumb-ml-place c shape)
             (thumb-tl-place c shape)
             (thumb-mr-place c shape)
             (thumb-br-place c shape)
             (thumb-bl-place c shape)))))

(defn thumb-15x-layout [c shape]
  (let [thumb-count (get c :configuration-thumb-count)]
    (case thumb-count
      :three (union (thumb-tr-place c shape)
                    (thumb-tl-place c shape)
                    (thumb-ml-place c shape))
      :five ()
      (thumb-tr-place c shape)
             )))

(def larger-plate
  (let [plate-height (/ (- sa-double-length mount-height) 3)
        top-plate    (->> (cube (+ mount-width 0.4) (- plate-height 0.2) web-thickness)
                          (translate [0.2
                                      (/ (+ plate-height mount-height -0.20) 2)
                                      (- plate-thickness (/ web-thickness 2))]))]
    (union top-plate (mirror [0 1 0] top-plate))))

(defn thumbcaps [c]
  (union
   (thumb-1x-layout c (sa-cap 1))
   (thumb-15x-layout c (rotate (/ pi 2) [0 0 1] (sa-cap 1.5)))))

(defn thumb [c]
  (union
   (thumb-1x-layout c (single-plate c))
   (thumb-15x-layout c (single-plate c))
   (thumb-15x-layout c larger-plate)))

(def thumb-post-tr
  (translate [(- (/ mount-width 2) post-adj -0.4) (- (/ mount-height  1.15) post-adj) 0] web-post))
(def thumb-post-tl
  (translate [(+ (/ mount-width -2) post-adj) (- (/ mount-height  1.15) post-adj) 0] web-post))
(def thumb-post-bl
  (translate [(+ (/ mount-width -2) post-adj) (+ (/ mount-height -1.15) post-adj) 0] web-post))
(def thumb-post-br
  (translate [(- (/ mount-width 2) post-adj -0.4)  (+ (/ mount-height -1.15) post-adj) 0] web-post))

(defn thumb-connector-two [c]
  (let [row-count (get c :configuration-last-row-count)
        lastrow   (flastrow (get c :configuration-nrows))
        cornerrow (fcornerrow (get c :configuration-nrows))]
    (union (triangle-hulls
            (thumb-tl-place c thumb-post-tr)
            (thumb-tl-place c thumb-post-br)
            (thumb-tr-place c thumb-post-tl)
            (thumb-tr-place c thumb-post-bl))
           (triangle-hulls    ; top two to the main keyboard, starting on the left
            (thumb-tl-place c thumb-post-tl)
            ;; (key-place c 0 cornerrow web-post-bl)
            (if (not= :innie (get c :configuration-inner-column))
              (key-place c 0 cornerrow web-post-bl)
              ())
            (thumb-tl-place c thumb-post-tr)
            (key-place c 0 cornerrow web-post-br)
            (thumb-tr-place c thumb-post-tl)
            (key-place c 1 cornerrow web-post-bl)
            (thumb-tr-place c thumb-post-tr)
            (key-place c 1 cornerrow web-post-br)
            (thumb-tr-place c thumb-post-br)
            (key-place c 2 cornerrow web-post-bl)
            (case row-count
              :zero ()
              (key-place c 2 lastrow web-post-bl))
            (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-bl)
            (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-br)
            (thumb-tr-place c thumb-post-br)
            (key-place c 3 (case row-count :zero cornerrow lastrow) web-post-bl))
           (triangle-hulls
            (key-place c 2 lastrow web-post-tl)
            (key-place c 2 cornerrow web-post-bl)
            (key-place c 2 lastrow web-post-tr)
            (key-place c 2 cornerrow web-post-br)
            (key-place c 3 cornerrow web-post-bl)))))

(defn thumb-connector-three [c]
  (let [row-count (get c :configuration-last-row-count)
        lastrow   (flastrow (get c :configuration-nrows))
        cornerrow (fcornerrow (get c :configuration-nrows))]
    (union
     (triangle-hulls    ; top two
      (thumb-tl-place c thumb-post-tr)
      (thumb-tl-place c thumb-post-br)
      (thumb-tr-place c thumb-post-tl)
      (thumb-tr-place c thumb-post-bl)
      (thumb-tl-place c thumb-post-br)
      (thumb-tl-place c thumb-post-bl))
     (triangle-hulls    ; top two to the middle two, starting on the left
      (thumb-tl-place c thumb-post-tl)
      (thumb-ml-place c thumb-post-tr)
      (thumb-tl-place c thumb-post-bl)
      (thumb-ml-place c thumb-post-br))
     (triangle-hulls    ; top two to the main keyboard, starting on the left
      (thumb-tl-place c thumb-post-tl)
      ;; (key-place c 0 cornerrow web-post-bl)
      (if (not= :innie (get c :configuration-inner-column))
        (key-place c 0 cornerrow web-post-bl)
        ())
      (thumb-tl-place c thumb-post-tr)
      (key-place c 0 cornerrow web-post-br)
      (thumb-tr-place c thumb-post-tl)
      (key-place c 1 cornerrow web-post-bl)
      (thumb-tr-place c thumb-post-tr)
      (key-place c 1 cornerrow web-post-br)
      (thumb-tr-place c thumb-post-br)
      (key-place c 2 cornerrow web-post-bl)
      (case row-count
        :zero ()
        (key-place c 2 lastrow web-post-bl))
      (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-bl)
      (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-br)
      (thumb-tr-place c thumb-post-br)
      (key-place c 3 (case row-count :zero cornerrow lastrow) web-post-bl))
     (triangle-hulls
      (thumb-tl-place c thumb-post-bl)
      (thumb-ml-place c thumb-post-br)
      (thumb-ml-place c thumb-post-bl))
     (triangle-hulls
      (key-place c 2 lastrow web-post-tl)
      (key-place c 2 cornerrow web-post-bl)
      (key-place c 2 lastrow web-post-tr)
      (key-place c 2 cornerrow web-post-br)
      (key-place c 3 cornerrow web-post-bl))
     (triangle-hulls
      (key-place c 3 lastrow web-post-tr)
      (key-place c 4 cornerrow web-post-bl)))))

(defn thumb-connector-four [c]
  (let [row-count (get c :configuration-last-row-count)
        lastrow (flastrow (get c :configuration-nrows))
        cornerrow (fcornerrow (get c :configuration-nrows))]
    (union (triangle-hulls
            (thumb-tl-place c thumb-post-tr)
            (thumb-tl-place c thumb-post-br)
            (thumb-tr-place c thumb-post-tl)
            (thumb-tr-place c thumb-post-bl))
           (triangle-hulls
            (thumb-ml-place c web-post-tr)
            (thumb-tl-place c thumb-post-tl)
            (thumb-ml-place c web-post-br)
            (thumb-tl-place c thumb-post-bl)
            (thumb-ml-place c web-post-bl)
            (thumb-mr-place c web-post-tl))
           (triangle-hulls
            (thumb-mr-place c web-post-tl)
            (thumb-tl-place c thumb-post-bl)
            (thumb-mr-place c web-post-tr)
            (thumb-tl-place c thumb-post-br)
            (thumb-mr-place c web-post-br)
            (thumb-tr-place c thumb-post-bl)
            (thumb-tr-place c thumb-post-br))
           (triangle-hulls    ; top two to the main keyboard, starting on the left
            (thumb-tl-place c thumb-post-tl)
            ;; (key-place c 0 cornerrow web-post-bl)
            (if (not= :innie (get c :configuration-inner-column))
              (key-place c 0 cornerrow web-post-bl)
              ())
            (thumb-tl-place c thumb-post-tr)
            (key-place c 0 cornerrow web-post-br)
            (thumb-tr-place c thumb-post-tl)
            (key-place c 1 cornerrow web-post-bl)
            (thumb-tr-place c thumb-post-tr)
            (key-place c 1 cornerrow web-post-br)
            (thumb-tr-place c thumb-post-br)
            (key-place c 2 cornerrow web-post-bl)
            (case row-count
              :zero ()
              (key-place c 2 lastrow web-post-bl))
            (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-bl)
            (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-br)
            (thumb-tr-place c thumb-post-br)
            (key-place c 3 (case row-count :zero cornerrow lastrow) web-post-bl))
           (triangle-hulls
            (key-place c 1 cornerrow web-post-br)
            (key-place c 2 lastrow web-post-tl)
            (key-place c 2 cornerrow web-post-bl)
            (key-place c 2 lastrow web-post-tr)
            (key-place c 2 cornerrow web-post-br)
            (key-place c 3 cornerrow web-post-bl)))))

(defn thumb-connector-five [c]
  (let [row-count (get c :configuration-last-row-count)
        lastrow   (flastrow (get c :configuration-nrows))
        cornerrow (fcornerrow (get c :configuration-nrows))]
    (union (triangle-hulls
            (thumb-tl-place c web-post-tr)
            (thumb-tl-place c web-post-br)
            (thumb-tr-place c web-post-tl)
            (thumb-tr-place c web-post-bl))
           (triangle-hulls    ; bottom two
            (thumb-br-place c web-post-tr)
            (thumb-br-place c web-post-br)
            (thumb-mr-place c web-post-tl)
            (thumb-mr-place c web-post-bl))
           (triangle-hulls
            (thumb-mr-place c web-post-tr)
            (thumb-mr-place c web-post-br)
            (thumb-tr-place c web-post-br))
           (triangle-hulls    ; between top row and bottom row
            (thumb-br-place c web-post-tl)
            (thumb-bl-place c web-post-bl)
            (thumb-br-place c web-post-tr)
            (thumb-bl-place c web-post-br)
            (thumb-mr-place c web-post-tl)
            (thumb-tl-place c web-post-bl)
            (thumb-mr-place c web-post-tr)
            (thumb-tl-place c web-post-br)
            (thumb-tr-place c web-post-bl)
            (thumb-mr-place c web-post-tr)
            (thumb-tr-place c web-post-br))
           (triangle-hulls    ; top two to the middle two, starting on the left
            (thumb-tl-place c web-post-tl)
            (thumb-bl-place c web-post-tr)
            (thumb-tl-place c web-post-bl)
            (thumb-bl-place c web-post-br)
            (thumb-mr-place c web-post-tr)
            (thumb-tl-place c web-post-bl)
            (thumb-tl-place c web-post-br)
            (thumb-mr-place c web-post-tr))
           (triangle-hulls    ; top two to the main keyboard, starting on the left
            (thumb-bl-place c web-post-tr)
            (key-place c 0 cornerrow web-post-bl)
            (thumb-tl-place c web-post-tl)
            (key-place c 0 cornerrow web-post-bl)
            (thumb-tl-place c web-post-tr)
            (key-place c 0 cornerrow web-post-br)
            (thumb-tr-place c web-post-tl)
            (key-place c 1 cornerrow web-post-bl)
            (thumb-tr-place c web-post-tr)
            (key-place c 1 cornerrow web-post-br)
            (thumb-tr-place c web-post-br)
            (key-place c 2 cornerrow web-post-bl)
            (case row-count
              :zero ()
              (key-place c 2 lastrow web-post-bl))
            (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-bl)
            (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-br)
            (thumb-tr-place c web-post-br)
            (key-place c 3 (case row-count :zero cornerrow lastrow) web-post-bl))
           (triangle-hulls
            (key-place c 1 cornerrow web-post-br)
            (key-place c 2 lastrow web-post-tl)
            (key-place c 2 cornerrow web-post-bl)
            (key-place c 2 lastrow web-post-tr)
            (key-place c 2 cornerrow web-post-br)
            (key-place c 3 cornerrow web-post-bl)))))

(defn thumb-connector-six [c]
  (let [row-count (get c :configuration-last-row-count)
        lastrow   (flastrow (get c :configuration-nrows))
        cornerrow (fcornerrow (get c :configuration-nrows))]
    (union 
           (triangle-hulls
            (thumb-tl-place c web-post-tr)
            (thumb-tl-place c web-post-br)
            (thumb-tr-place c thumb-post-tl)
            (thumb-tr-place c thumb-post-bl))
           (triangle-hulls
            (thumb-tl-place c thumb-post-tl)
            (thumb-ml-place c web-post-tr)
            (thumb-tl-place c web-post-tl)
            (thumb-ml-place c web-post-br)
            (thumb-tl-place c web-post-bl))
           (triangle-hulls
            (thumb-mr-place c web-post-tl)
            (thumb-tl-place c web-post-bl)
            (thumb-mr-place c web-post-tr)
            (thumb-tl-place c web-post-br)
            (thumb-mr-place c web-post-br)
            (thumb-tr-place c thumb-post-bl)
            (thumb-tr-place c thumb-post-br))
           (triangle-hulls
            (thumb-ml-place c web-post-tl)
            (thumb-bl-place c web-post-tr)
            (thumb-ml-place c web-post-bl)
            (thumb-bl-place c web-post-br))
           ;; special wall
           (triangle-hulls
            (thumb-br-place c web-post-br)
            (thumb-mr-place c web-post-br)
            (thumb-mr-place c web-post-bl))
           (triangle-hulls
            (thumb-br-place c web-post-tr)
            (thumb-mr-place c web-post-tl)
            (thumb-br-place c web-post-br)
            (thumb-mr-place c web-post-bl))
           (triangle-hulls    ; centers of the bottom four
            (thumb-br-place c web-post-tl)
            (thumb-bl-place c web-post-bl)
            (thumb-br-place c web-post-tr)
            (thumb-bl-place c web-post-br)
            (thumb-mr-place c web-post-tl)
            (thumb-ml-place c web-post-bl)
            (thumb-tl-place c web-post-bl)
            (thumb-ml-place c web-post-br)
            )
           (triangle-hulls    ; top two to the main keyboard, starting on the left
            (thumb-tl-place c web-post-tl)
            (thumb-tl-place c thumb-post-tl)
            (thumb-tl-place c web-post-tr)
            ;; (key-place c 0 cornerrow web-post-bl)
            (if (not= :innie (get c :configuration-inner-column))
              (key-place c 0 cornerrow web-post-bl)
              ())
            (thumb-tr-place c thumb-post-tl)
            (key-place c 0 cornerrow web-post-br)
            (thumb-tr-place c thumb-post-tr)
            (key-place c 1 cornerrow web-post-bl)
            (key-place c 1 cornerrow web-post-br)
            (thumb-tr-place c thumb-post-tr)
            (key-place c 2 lastrow web-post-tl)
            (thumb-tr-place c thumb-post-br)
            (case row-count
              :zero ()
              (key-place c 2 lastrow web-post-bl))
            (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-bl)
            (key-place c 2 (case row-count :zero cornerrow lastrow) web-post-br)
            (thumb-tr-place c thumb-post-br)
            (key-place c 3 (case row-count :zero cornerrow lastrow) web-post-bl))
           (triangle-hulls
            (key-place c 1 cornerrow web-post-br)
            (key-place c 2 lastrow web-post-tl)
            (key-place c 2 cornerrow web-post-bl)
            (key-place c 2 lastrow web-post-tr)
            (key-place c 2 cornerrow web-post-br)
            (key-place c 3 cornerrow web-post-bl))
           )))

(defn thumb-connectors [c]
  (let [thumb-count (get c :configuration-thumb-count)]
    (case thumb-count
      :two (thumb-connector-two c)
      :three (thumb-connector-three c)
      :four (thumb-connector-four c)
      :five (thumb-connector-five c)
      (thumb-connector-six c))))

;;;;;;;;;;
;; Case ;;
;;;;;;;;;;
(defn inner-key-position [c row direction]
  (map -
       (key-position c -1 row [(* mount-width -0.5) (* direction mount-height 0.5) 0])
       [left-wall-x-offset 0 left-wall-z-offset]))

(defn left-key-place [c row direction shape]
  (translate (left-key-position c row direction) shape))

(defn index-key-place [c row direction shape]
  (translate (index-key-position c row direction) shape))

(defn inner-key-place [c row direction shape]
  (translate (inner-key-position c row direction) shape))

(defn wall-brace
  "If you want to change the wall, use this.
   place1 means the location at the keyboard, marked by key-place or thumb-xx-place
   dx1 means the movement from place1 in x coordinate, multiplied by wall-xy-locate.
   dy1 means the movement from place1 in y coordinate, multiplied by wall-xy-locate.
   post1 means the position this wall attached to place1.
         xxxxx-br means bottom right of the place1.
         xxxxx-bl means bottom left of the place1.
         xxxxx-tr means top right of the place1.
         xxxxx-tl means top left of the place1.
   place2 means the location at the keyboard, marked by key-place or thumb-xx-place
   dx2 means the movement from place2 in x coordinate, multiplied by wall-xy-locate.
   dy2 means the movement from place2 in y coordinate, multiplied by wall-xy-locate.
   post2 means the position this wall attached to place2.
         xxxxx-br means bottom right of the place2.
         xxxxx-bl means bottom left of the place2.
         xxxxx-tr means top right of the place2.
         xxxxx-tl means top left of the place2.
   How does it work?
   Given the following wall
       a ==\\ b
            \\
           c \\ d
             | |
             | |
             | |
             | |
           e | | f
   In this function a: usually the wall of a switch hole.
                    b: the result of hull and translation from wall-locate1
                    c: the result of hull and translation from wall-locate2
                    d: the result of hull and translation from wall-locate3
                    e: the result of bottom-hull translation from wall-locate2
                    f: the result of bottom-hull translation from wall-locate3"
  [place1 dx1 dy1 post1 place2 dx2 dy2 post2]
  (union
   (hull
    (place1 post1)
    (place1 (translate (wall-locate1 dx1 dy1) post1))
    (place1 (translate (wall-locate2 dx1 dy1) post1))
    (place1 (translate (wall-locate3 dx1 dy1) post1))
    (place2 post2)
    (place2 (translate (wall-locate1 dx2 dy2) post2))
    (place2 (translate (wall-locate2 dx2 dy2) post2))
    (place2 (translate (wall-locate3 dx2 dy2) post2)))
   (bottom-hull
    (place1 (translate (wall-locate2 dx1 dy1) post1))
    (place1 (translate (wall-locate3 dx1 dy1) post1))
    (place2 (translate (wall-locate2 dx2 dy2) post2))
    (place2 (translate (wall-locate3 dx2 dy2) post2)))))

(defn key-wall-brace [c x1 y1 dx1 dy1 post1 x2 y2 dx2 dy2 post2]
  (wall-brace (partial key-place c x1 y1) dx1 dy1 post1
              (partial key-place c x2 y2) dx2 dy2 post2))

(defn right-wall [c]
  (let [row-count       (get c :configuration-last-row-count)
        use-wide-pinky? (get c :configuration-use-wide-pinky?)
        lastcol         (flastcol (get c :configuration-ncols))
        lastrow         (flastrow (get c :configuration-nrows))
        cornerrow       (fcornerrow (get c :configuration-nrows))]
    (union (key-wall-brace c
                           lastcol 0 0 1 (wide-post-tr use-wide-pinky?)
                           lastcol 0 1 0 (wide-post-tr use-wide-pinky?))
           (for [y (range 0 lastrow)]
             (key-wall-brace c
                             lastcol y 1 0 (wide-post-tr use-wide-pinky?)
                             lastcol y 1 0 (wide-post-br use-wide-pinky?)))
           (case row-count
             :full (key-wall-brace c
                                   lastcol lastrow 1 0 (wide-post-tr use-wide-pinky?)
                                   lastcol lastrow 1 0 (wide-post-br use-wide-pinky?))
             ())
           (for [y (range 1 lastrow)]
             (key-wall-brace c
                             lastcol (dec y) 1 0 (wide-post-br use-wide-pinky?)
                             lastcol y 1 0 (wide-post-tr use-wide-pinky?)))
           (case row-count
             :full (key-wall-brace c
                                   lastcol (dec lastrow) 1 0 (wide-post-br use-wide-pinky?)
                                   lastcol lastrow       1 0 (wide-post-tr use-wide-pinky?))
             ())
           (key-wall-brace c
                           lastcol (case row-count :full lastrow cornerrow) 0 -1 (wide-post-br use-wide-pinky?)
                           lastcol (case row-count :full lastrow cornerrow) 1  0 (wide-post-br use-wide-pinky?)))))

(defn back-wall [c]
  (let [ncols             (get c :configuration-ncols)
        lastcol           (flastcol ncols)
        inner             (get c :configuration-inner-column)
        init              (case inner
                            :outie -1
                            :normie 0
                            :innie 1)]
    (union
     (for [x (range init ncols)]
       (key-wall-brace c x 0 0 1 web-post-tl x       0 0 1 web-post-tr))
     (for [x (range (+ init 1) ncols)]
       (key-wall-brace c x 0 0 1 web-post-tl (dec x) 0 0 1 web-post-tr))
     (key-wall-brace c lastcol 0 0 1 web-post-tr lastcol 0 1 0 web-post-tr))))

(defn left-wall [c]
  (let [nrows             (get c :configuration-nrows)
        inner             (get c :configuration-inner-column)
        lastrow           (flastrow nrows)
        cornerrow         (fcornerrow nrows)
        end               (case inner
                            :outie cornerrow
                            lastrow)
        partial-place     (case inner
                            :outie (partial inner-key-place c)
                            :normie (partial left-key-place c)
                            :innie (partial index-key-place c))
        init              (case inner :outie -1 :normie 0 :innie 1)]
    (union
     (for [y (range 0 end)]
       (union
        (wall-brace (partial partial-place y 1) -1 0 web-post
                    (partial partial-place y -1) -1 0 web-post)
        (hull (key-place c init y web-post-tl)
              (key-place c init y web-post-bl)
              (partial-place y  1 web-post)
              (partial-place y -1 web-post))))
     (for [y (range 1 (case inner :outie cornerrow lastrow))]
       (union
        (wall-brace (partial partial-place (dec y) -1) -1 0 web-post
                    (partial partial-place y        1) -1 0 web-post)
        (hull (key-place c init y       web-post-tl)
              (key-place c init (dec y) web-post-bl)
              (partial-place y        1 web-post)
              (partial-place (dec y) -1 web-post))))
     (wall-brace (partial key-place c init 0) 0 1 web-post-tl
                 (partial partial-place 0 1)  0 2.2 web-post)
     (wall-brace (partial partial-place 0 1)  0 2.2 web-post
                 (partial partial-place 0 1) -1 0 web-post))))

(defn front-wall [c]
  (let [ncols         (get c :configuration-ncols)
        nrows         (get c :configuration-nrows)
        lastrow       (flastrow nrows)
        cornerrow     (fcornerrow nrows)
        row-count     (get c :configuration-last-row-count)
        thumb-tr-post (if (= (get c :configuration-thumb-count) :five) web-post-br thumb-post-br)]
    (union
     (wall-brace (partial thumb-tr-place c)  0 -1 thumb-tr-post
                 (partial (partial key-place c) 3 (case row-count :zero cornerrow lastrow))  0 -1 web-post-bl)
     (key-wall-brace c
                     3 (case row-count :zero cornerrow lastrow) 0   -1 web-post-bl
                     3 (case row-count :zero cornerrow lastrow) 0.5 -1 web-post-br)
     (key-wall-brace c
                     3 (case row-count :zero cornerrow lastrow)   0.5 -1 web-post-br
                     4 (case row-count :full lastrow   cornerrow) 0   -1 web-post-bl)
     (for [x (range 4 ncols)]
       (key-wall-brace c
                       x (case row-count :full lastrow cornerrow) 0 -1 web-post-bl
                       x (case row-count :full lastrow cornerrow) 0 -1 web-post-br))
     (for [x (range 5 ncols)]
       (key-wall-brace c
                       x       (case row-count :full lastrow cornerrow) 0 -1 web-post-bl
                       (dec x) (case row-count :full lastrow cornerrow) 0 -1 web-post-br)))))

(defn pinky-connectors [c]
  (let [row-count       (get c :configuration-last-row-count)
        use-wide-pinky? (get c :configuration-use-wide-pinky?)
        lastcol         (flastcol (get c :configuration-ncols))
        lastrow         (flastrow (get c :configuration-nrows))
        cornerrow       (fcornerrow (get c :configuration-nrows))]
    (if-not use-wide-pinky?
      ()
      (apply union
             (concat
              (for [row (range 0 (case row-count :full (inc lastrow) lastrow))]
                (triangle-hulls
                 (key-place c lastcol row web-post-tr)
                 (key-place c lastcol row (wide-post-tr use-wide-pinky?))
                 (key-place c lastcol row web-post-br)
                 (key-place c lastcol row (wide-post-br use-wide-pinky?))))
              (for [row (range 0 (case row-count :full lastrow cornerrow))]
                (triangle-hulls
                 (key-place c lastcol row       web-post-br)
                 (key-place c lastcol row       (wide-post-br use-wide-pinky?))
                 (key-place c lastcol (inc row) web-post-tr)
                 (key-place c lastcol (inc row) (wide-post-tr use-wide-pinky?)))))))))

(defn pinky-wall [c]
  (let [row-count       (get c :configuration-last-row-count)
        use-wide-pinky? (get c :configuration-use-wide-pinky?)
        lastcol         (flastcol (get c :configuration-ncols))
        lastrow         (flastrow (get c :configuration-nrows))
        cornerrow       (fcornerrow (get c :configuration-nrows))]
    (if-not use-wide-pinky?
      ()
      (union
       (key-wall-brace c
                       lastcol (case row-count :full lastrow cornerrow) 0 -1 web-post-br
                       lastcol (case row-count :full lastrow cornerrow) 0 -1 (wide-post-br use-wide-pinky?))
       (key-wall-brace c
                       lastcol 0 0 1 web-post-tr
                       lastcol 0 0 1 (wide-post-tr use-wide-pinky?))))))

(defn thumb-wall-two [c]
  (union (wall-brace (partial thumb-tr-place c)  0 -1 thumb-post-br
                     (partial thumb-tr-place c)  0 -1 thumb-post-bl)
         (wall-brace (partial thumb-tr-place c)  0 -1 thumb-post-bl
                     (partial thumb-tl-place c)  0 -1 thumb-post-br)
         (wall-brace (partial thumb-tl-place c)  0 -1 thumb-post-br
                     (partial thumb-tl-place c)  0 -1 thumb-post-bl)
         (wall-brace (partial thumb-tl-place c)  0 -1 thumb-post-bl
                     (partial thumb-tl-place c) -1  0 thumb-post-bl)
         (wall-brace (partial thumb-tl-place c) -1  0 thumb-post-bl
                     (partial thumb-tl-place c) -1  0 thumb-post-tl)))

(defn thumb-wall-three [c]
  (union (wall-brace (partial thumb-tr-place c)  0 -1 thumb-post-br
                     (partial thumb-tr-place c)  0 -1 thumb-post-bl)
         (wall-brace (partial thumb-tr-place c)  0 -1 thumb-post-bl
                     (partial thumb-tl-place c)  0 -1 thumb-post-bl)
         (wall-brace (partial thumb-tl-place c)  0 -1 thumb-post-bl
                     (partial thumb-ml-place c) -1 -1 thumb-post-br)
         (wall-brace (partial thumb-ml-place c) -1 -1 thumb-post-br
                     (partial thumb-ml-place c)  0 -1 thumb-post-bl)
         (wall-brace (partial thumb-ml-place c)  0 -1 thumb-post-bl
                     (partial thumb-ml-place c) -1  0 thumb-post-bl)
         (wall-brace (partial thumb-ml-place c) -1  0 thumb-post-bl
                     (partial thumb-ml-place c) -1  0 thumb-post-tl)
         (wall-brace (partial thumb-ml-place c) -1  0 thumb-post-tl
                     (partial thumb-ml-place c)  0  1 thumb-post-tl)
         (wall-brace (partial thumb-ml-place c)  0  1 thumb-post-tr
                     (partial thumb-ml-place c)  0  1 thumb-post-tl)))

(defn thumb-wall-four [c]
  (union (wall-brace (partial thumb-tr-place c)  0 -1 thumb-post-br
                     (partial thumb-mr-place c)  0 -1 web-post-br)
         (wall-brace (partial thumb-mr-place c)  0 -1 web-post-br
                     (partial thumb-mr-place c)  0 -1 web-post-bl)
         (wall-brace (partial thumb-mr-place c)  0 -1 web-post-bl
                     (partial thumb-mr-place c) -1  0 web-post-bl)
         (wall-brace (partial thumb-mr-place c) -1  0 web-post-bl
                     (partial thumb-mr-place c) -1  0 web-post-tl)
         (wall-brace (partial thumb-mr-place c) -1  0 web-post-tl
                     (partial thumb-ml-place c) -1  0 web-post-bl)
         (wall-brace (partial thumb-ml-place c) -1  0 web-post-bl
                     (partial thumb-ml-place c) -1  0 web-post-tl)
         (wall-brace (partial thumb-ml-place c) -1  0 web-post-tl
                     (partial thumb-ml-place c)  0  1 web-post-tl)
         (wall-brace (partial thumb-ml-place c)  0  1 web-post-tl
                     (partial thumb-ml-place c)  0  1 web-post-tr)))

(defn thumb-wall-five [c]
  (union (wall-brace (partial thumb-tr-place c)  0  -1 web-post-br
                     (partial thumb-mr-place c)  0  -1 web-post-br)
         (wall-brace (partial thumb-mr-place c)  0  -1 web-post-br
                     (partial thumb-mr-place c)  0  -1 web-post-bl)
         (wall-brace (partial thumb-mr-place c)  0  -1 web-post-bl
                     (partial thumb-br-place c)  0  -1 web-post-br)
         (wall-brace (partial thumb-br-place c)  0  -1 web-post-br
                     (partial thumb-br-place c)  0  -1 web-post-bl)
         (wall-brace (partial thumb-br-place c)  0  -1 web-post-bl
                     (partial thumb-br-place c) -1   0 web-post-bl)
         (wall-brace (partial thumb-br-place c) -1   0 web-post-bl
                     (partial thumb-br-place c) -1   0 web-post-tl)
         (wall-brace (partial thumb-br-place c) -1   0 web-post-tl
                     (partial thumb-bl-place c) -1   0 web-post-bl)
         (wall-brace (partial thumb-bl-place c) -1   0 web-post-bl
                     (partial thumb-bl-place c) -1   0 web-post-tl)
         (wall-brace (partial thumb-bl-place c) -1   0 web-post-tl
                     (partial thumb-bl-place c)  0   1 web-post-tl)
         (wall-brace (partial thumb-bl-place c)  0   1 web-post-tl
                     (partial thumb-bl-place c) -0.5 1 web-post-tr)))

(defn thumb-wall-six [c]
  (union (wall-brace (partial thumb-tr-place c)  0 -1 thumb-post-br
                     (partial thumb-mr-place c)  0 -1 web-post-br)
         (wall-brace (partial thumb-mr-place c)  0 -1 web-post-br
                     (partial thumb-br-place c)  0 -1 web-post-br)
         (wall-brace (partial thumb-br-place c)  0 -1 web-post-br
                     (partial thumb-br-place c)  0 -1 web-post-bl)
         (wall-brace (partial thumb-br-place c)  0 -1 web-post-bl
                     (partial thumb-br-place c) -1  0 web-post-bl)
         (wall-brace (partial thumb-br-place c) -1  0 web-post-bl
                     (partial thumb-br-place c) -1  0 web-post-tl)
         (wall-brace (partial thumb-br-place c) -1  0 web-post-tl
                     (partial thumb-bl-place c) -1  0 web-post-bl)
         (wall-brace (partial thumb-bl-place c) -1  0 web-post-bl
                     (partial thumb-bl-place c) -1  0 web-post-tl)
         (wall-brace (partial thumb-bl-place c) -1  0 web-post-tl
                     (partial thumb-bl-place c)  0  1 web-post-tl)
         (wall-brace (partial thumb-bl-place c)  0  1 web-post-tl
                     (partial thumb-bl-place c) -1.1  1 web-post-tr)
         (wall-brace (partial thumb-bl-place c) -1.1  1 web-post-tr
                     (partial thumb-ml-place c) -1.1  1 web-post-tl)
         (wall-brace (partial thumb-ml-place c) -1.1  1 web-post-tl
                     (partial thumb-ml-place c)  0  1 web-post-tr)))

(defn thumb-wall [c]
  (let [thumb-count (get c :configuration-thumb-count)]
    (case thumb-count
      :two (thumb-wall-two c)
      :three (thumb-wall-three c)
      :four (thumb-wall-four c)
      :five (thumb-wall-five c)
      (thumb-wall-six c))))

(defn second-thumb-to-body [c]
  (let [thumb-count      (get c :configuration-thumb-count)
        inner            (get c :configuration-inner-column)
        nrows            (get c :configuration-nrows)
        cornerrow        (fcornerrow nrows)
        middlerow        (fmiddlerow nrows)
        inner-placement  (case inner
                           :outie (partial inner-key-place)
                           :normie (partial left-key-place)
                           :innie (partial index-key-place))
        innerrow         (case inner
                           :outie middlerow
                           cornerrow)
        init             (case inner
                           :outie -1
                           :normie 0
                           :innie 1)
        body-gap-default (bottom-hull
                          (inner-placement c innerrow -1 (translate (wall-locate2 -1 0) web-post))
                          (inner-placement c innerrow -1 (translate (wall-locate3 -1 0) web-post))
                          (thumb-ml-place c (translate (wall-locate2 -0.3 1) (if (= thumb-count :three) thumb-post-tr web-post-tr)))
                          (thumb-ml-place c (translate (wall-locate3 -0.3 1) (if (= thumb-count :three) thumb-post-tr web-post-tr))))
        body-gap-two     (bottom-hull
                          (inner-placement c innerrow -1 (translate (wall-locate2 -1 0) web-post))
                          (inner-placement c innerrow -1 (translate (wall-locate3 -1 0) web-post))
                          (thumb-tl-place c (translate (wall-locate2 -1 0) thumb-post-tl))
                          (thumb-tl-place c (translate (wall-locate3 -1 0) thumb-post-tl)))
        body-gap-five    (bottom-hull
                          (inner-placement c innerrow -1 (translate (wall-locate2 -1 0) web-post))
                          (inner-placement c innerrow -1 (translate (wall-locate3 -1 0) web-post))
                          (thumb-tl-place c (translate (wall-locate2 -0.9 0) thumb-post-tl))
                          (thumb-tl-place c (translate (wall-locate3 -0.9 0) thumb-post-tl)))]
    (union
     (case thumb-count
       :two body-gap-two
       :five body-gap-five
       body-gap-default)
     (if (= thumb-count :five)
       (hull (key-place c 0 cornerrow web-post-bl)
             (thumb-bl-place c web-post-tr)
             (thumb-tl-place c thumb-post-tl))
       ())
     (hull
      (inner-placement c innerrow -1 (translate (wall-locate2 -1 0) web-post))
      (inner-placement c innerrow -1 (translate (wall-locate3 -1 0) web-post))
      (case thumb-count
        :two ()
        :five (thumb-bl-place c (translate (wall-locate2 -0.5 1) web-post-tr))
        (thumb-ml-place c (translate (wall-locate2 -0.3 1) (if (= thumb-count :three) thumb-post-tr web-post-tr))))
      (case thumb-count
        :two (thumb-tl-place c (translate (wall-locate3 -1 0) thumb-post-tl))
        :five (thumb-bl-place c (translate (wall-locate3 -0.5 1) web-post-tr))
        (thumb-ml-place c (translate (wall-locate3 -0.3 1) (if (= thumb-count :three) thumb-post-tr web-post-tr))))
      (thumb-tl-place c thumb-post-tl))
     (hull
      (inner-placement c innerrow -1 web-post)
      (inner-placement c innerrow -1 (translate (wall-locate1 -1 0) web-post))
      (inner-placement c innerrow -1 (translate (wall-locate2 -1 0) web-post))
      (inner-placement c innerrow -1 (translate (wall-locate3 -1 0) web-post))
      (thumb-tl-place c thumb-post-tl))
     (hull
      (inner-placement c innerrow -1 web-post)
      (inner-placement c innerrow -1 (translate (wall-locate1 0 0) web-post))
      (key-place c init innerrow web-post-bl)
      (key-place c init innerrow (translate (wall-locate1 0 0) web-post-bl))
      (thumb-tl-place c thumb-post-tl))
     (case inner
       :outie (triangle-hulls
               (thumb-tl-place c thumb-post-tl)
               (key-place c  0 cornerrow web-post-bl)
               (key-place c -1 middlerow web-post-bl)
               (key-place c -1 cornerrow web-post-tr))
       ())
     (case thumb-count
       :two ()
       :five (hull (thumb-bl-place c web-post-tr)
                   (thumb-bl-place c (translate (wall-locate1 -0.5 1) web-post-tr))
                   (thumb-bl-place c (translate (wall-locate2 -0.5 1) web-post-tr))
                   (thumb-bl-place c (translate (wall-locate3 -0.5 1) web-post-tr))
                   (thumb-tl-place c thumb-post-tl))
       (hull (thumb-ml-place c (if (= thumb-count :three) thumb-post-tr web-post-tr))
             (thumb-ml-place c (translate (wall-locate1 -0.4 1) (if (= thumb-count :three) thumb-post-tr web-post-tr)))
             (thumb-ml-place c (translate (wall-locate2 -0.4 1) (if (= thumb-count :three) thumb-post-tr web-post-tr)))
             (thumb-ml-place c (translate (wall-locate3 -0.4 1) (if (= thumb-count :three) thumb-post-tr web-post-tr)))
             (thumb-tl-place c thumb-post-tl))))))

(defn case-walls [c]
  (union
   (back-wall c)
   (right-wall c)
   (left-wall c)
   (front-wall c)
   (pinky-wall c)
   (pinky-connectors c)
   (thumb-wall c)
   (second-thumb-to-body c)))

(defn frj9-start [c]
  (let [x-position (case (get c :configuration-inner-column)
                     :outie 0
                     :normie 0.1
                     :innie 1)]
    (map + [0 -3  0] (key-position c x-position 0 (map + (wall-locate3 0 1) [0 (/ mount-height  2) 0])))))

(defn fusb-holder-position [c]
  (let [x-position (case (get c :configuration-inner-column)
                     :outie 1
                     :normie 1
                     :innie 1.6)]
    (key-position c x-position 0 (map + (wall-locate2 0 1) [0 (/ mount-height 2) 0]))))

(defn trrs-usb-holder-ref [c]
  (let [nrows      (get c :configuration-nrows)
        y-addition (case nrows
                     2 0
                     3 0
                     4 0
                     5 -1
                     6 -2
                     -3)]
    (key-position c 1 0 (map - (wall-locate2  0 y-addition) [0 (/ mount-height 2) 0]))))

(defn trrs-usb-holder-position [c]
  (map + [17 19.3 0] [(first (trrs-usb-holder-ref c)) (second (trrs-usb-holder-ref c)) 2]))
(def trrs-usb-holder-cube
  (cube 15 12 2))
(defn trrs-usb-holder-space [c]
  (translate (map + (trrs-usb-holder-position c) [0 (* -1 wall-thickness) 1]) trrs-usb-holder-cube))
(defn trrs-usb-holder-holder [c]
  (translate (trrs-usb-holder-position c) (cube 19 12 4)))

(defn trrs-usb-jack [c] (translate (map + (trrs-usb-holder-position c) [0 10 3]) (cube 8.1 40 3.1)))

(def trrs-holder-size [6.2 10 2]) ; trrs jack PJ-320A
(def trrs-holder-hole-size [6.2 11 6]) ; trrs jack PJ-320A
(defn trrs-holder-position [c]
  (map + (trrs-usb-holder-position c) [-13.6 0 0]))
(def trrs-holder-thickness 2)
(def trrs-holder-thickness-2x (* 2 trrs-holder-thickness))
(defn trrs-holder [c]
  (union
   (->> (cube (+ (first trrs-holder-size) trrs-holder-thickness-2x)
              (+ trrs-holder-thickness (second trrs-holder-size))
              (+ (last trrs-holder-size) trrs-holder-thickness))
        (translate [(first (trrs-holder-position c))
                    (second (trrs-holder-position c))
                    (/ (+ (last trrs-holder-size) trrs-holder-thickness) 2)]))))
(defn trrs-holder-hole [c]
  (union
   (->>
    (->> (binding [*fn* 30] (cylinder 2.55 40))) ; 5mm trrs jack
    (rotate (deg2rad  90) [1 0 0])
    (translate [(first (trrs-holder-position c))
                (+ (second (trrs-holder-position c))
                   (/ (+ (second trrs-holder-size) trrs-holder-thickness) 2))
                (+ 3 (/ (+ (last trrs-holder-size) trrs-holder-thickness) 2))])) ;1.5 padding
  ; rectangular trrs holder
   (->> (apply cube trrs-holder-hole-size)
        (translate [(first (trrs-holder-position c))
                    (+ (/ trrs-holder-thickness -2) (second (trrs-holder-position c)))
                    (+ (/ (last trrs-holder-hole-size) 2) trrs-holder-thickness)]))))

(defn pro-micro-position [c]
  (map + (key-position c 0 0.15 (wall-locate3 -1 0)) [-2 2 -30]))
(def pro-micro-space-size [4 10 12]) ; z has no wall;
(def pro-micro-wall-thickness 2)
(def pro-micro-holder-size
  [(+ pro-micro-wall-thickness (first pro-micro-space-size))
   (+ pro-micro-wall-thickness (second pro-micro-space-size))
   (last pro-micro-space-size)])
(defn pro-micro-space [c]
  (->> (cube (first pro-micro-space-size)
             (second pro-micro-space-size)
             (last pro-micro-space-size))
       (translate [(- (first (pro-micro-position c)) (/ pro-micro-wall-thickness 2))
                   (- (second (pro-micro-position c)) (/ pro-micro-wall-thickness 2))
                   (last (pro-micro-position c))])))
(defn pro-micro-holder [c]
  (difference
   (->> (cube (first pro-micro-holder-size)
              (second pro-micro-holder-size)
              (last pro-micro-holder-size))
        (translate [(first (pro-micro-position c))
                    (second (pro-micro-position c))
                    (last (pro-micro-position c))]))
   (pro-micro-space c)))

(def teensy-width 20)
(def teensy-height 12)
(def teensy-length 33)
(def teensy2-length 53)
(def teensy-pcb-thickness 2)
(def teensy-holder-width  (+ 7 teensy-pcb-thickness))
(def teensy-holder-height (+ 6 teensy-width))
(def teensy-offset-height 5)
(def teensy-holder-top-length 18)
(defn teensy-top-xy [c]
  (key-position c 0 (- (fcenterrow (get c :configuration-nrows)) 1) (wall-locate3 -1 0)))
(defn teensy-bot-xy [c]
  (key-position c 0 (+ (fcenterrow (get c :configuration-nrows)) 1) (wall-locate3 -1 0)))
(defn teensy-holder-length [c]
  (- (second (teensy-top-xy c)) (second (teensy-bot-xy c))))
(defn teensy-holder-offset [c]
  (/ (teensy-holder-length c) -2))
(defn teensy-holder-top-offset [c]
  (- (/ teensy-holder-top-length 2) (teensy-holder-length c)))

(defn teensy-holder [c]
  (->>
   (union
    (->> (cube 3 (teensy-holder-length c) (+ 6 teensy-width))
         (translate [1.5 (teensy-holder-offset c) 0]))
    (->> (cube teensy-pcb-thickness (teensy-holder-length c) 3)
         (translate [(+ (/ teensy-pcb-thickness 2) 3) (teensy-holder-offset c) (- -1.5 (/ teensy-width 2))]))
    (->> (cube 4 (teensy-holder-length c) 4)
         (translate [(+ teensy-pcb-thickness 5) (teensy-holder-offset c) (-  -1 (/ teensy-width 2))]))
    (->> (cube teensy-pcb-thickness teensy-holder-top-length 3)
         (translate [(+ (/ teensy-pcb-thickness 2) 3) (teensy-holder-top-offset c) (+ 1.5 (/ teensy-width 2))]))
    (->> (cube 4 teensy-holder-top-length 4)
         (translate [(+ teensy-pcb-thickness 5) (teensy-holder-top-offset c) (+ 1 (/ teensy-width 2))])))
   (translate [(- teensy-holder-width) 0 0])
   (translate [-1.4 0 0])
   (translate [(first (teensy-top-xy c))
               (- (second (teensy-top-xy c)) 1)
               (/ (+ 6 teensy-width) 2)])))

; Offsets for the controller/trrs external holder cutout
(defn external-holder-offset [c]
  (case (get c :configuration-nrows)
    1 -9
    2 -7
    3 -5
    4 -3.5
    5 1.4
    6 2.2
    5))

; Cutout for controller/trrs jack external holder
(defn external-holder-ref [c]
  (key-position c 0 0 (map - (wall-locate2  0  -1) [0 (/ mount-height 2) 0])))
(defn external-holder-position [c]
  (map + [(+ 18.8 (external-holder-offset c)) 18.7 1.3] [(first (external-holder-ref c)) (second (external-holder-ref c)) 2]))
(def external-holder-cube
  (cube 28.466 30 12.6))
(defn external-holder-space [c]
  (translate (map + (external-holder-position c) [-1.5 (* -1 wall-thickness) 3]) external-holder-cube))

(defn screw-placement [c bottom-radius top-radius height]
  (let [use-wide-pinky? (get c :configuration-use-wide-pinky?)
        inner           (get c :configuration-inner-column)
        first-screw-x   (case inner
                          :innie 1
                          :normie 0
                          :outie -1)
        second-screw-x  (case inner
                          :innie 1.5
                          :normie 0
                          :outie -1.5)
        lastcol         (flastcol (get c :configuration-ncols))
        lastrow         (flastrow (get c :configuration-nrows))
        lastloc         (if-not use-wide-pinky? (+ lastcol 0.1) (+ lastcol 0.5))
        thumb-count     (get c :configuration-thumb-count)
        is-five?        (= thumb-count :five)
        var-middle-last (if is-five? -0 0.2)
        y-middle-last   (+ lastrow var-middle-last)
        x-middle-last   (if is-five? 1.6 2)]
    (union (screw-insert c first-screw-x  0               bottom-radius top-radius height)
           (screw-insert c second-screw-x (- lastrow 0.8) bottom-radius top-radius height)
           (screw-insert c x-middle-last  y-middle-last   bottom-radius top-radius height)
           (screw-insert c 3              0               bottom-radius top-radius height)
           (screw-insert c lastloc        1               bottom-radius top-radius height))))

(def wire-post-height 7)
(def wire-post-overhang 3.5)
(def wire-post-diameter 2.6)
(defn wire-post [c direction offset]
  (->> (union (translate [0 (* wire-post-diameter -0.5 direction) 0]
                         (cube wire-post-diameter wire-post-diameter wire-post-height))
              (translate [0 (* wire-post-overhang -0.5 direction) (/ wire-post-height -2)]
                         (cube wire-post-diameter wire-post-overhang wire-post-diameter)))
       (translate [0 (- offset) (+ (/ wire-post-height -2) 3)])
       (rotate (/ (get c :configuration-alpha) -2) [1 0 0])
       (translate [3 (/ mount-height -2) 0])))

(defn wire-posts [c]
  (union
   (thumb-ml-place c (translate [-5 0 -2]  (wire-post c  1 0)))
   (thumb-ml-place c (translate [0 0 -2.5] (wire-post c -1 6)))
   (thumb-ml-place c (translate [5 0 -2]   (wire-post c  1 0)))
   (for [column (range 0 (flastcol (get c :configuration-ncols)))
         row (range 0 (fcornerrow (get c :configuration-nrows)))]
     (union
      (key-place c column row (translate [-5 0 0] (wire-post c  1 0)))
      (key-place c column row (translate [0 0 0]  (wire-post c -1 6)))
      (key-place c column row (translate [5 0 0]  (wire-post c  1 0)))))))

(defn model-right [c]
  (let [show-caps?                 (get c :configuration-show-caps?)
        use-external-holder?       (get c :configuration-use-external-holder?)
        use-promicro-usb-hole?     (get c :configuration-use-promicro-usb-hole?)
        use-screw-inserts?         (get c :configuration-use-screw-inserts?)
        use-trrs?                  (get c :configuration-use-trrs?)
        use-wire-post?             (get c :configuration-use-wire-post?)]
    (difference
     (union
      (if show-caps? (caps c) ())
      (if show-caps? (thumbcaps c) ())
      (if-not use-external-holder?
        (union
         (if use-wire-post? (wire-posts c) ())
         (if-not use-trrs? (rj9-holder frj9-start c) ()))
        ())
      (key-holes c)
      (thumb c)
      (connectors c)
      (thumb-connectors c)
      (difference
       (union (case-walls c)
              (if use-screw-inserts? (screw-insert-outers screw-placement c) ())
              (if-not use-external-holder?
                (union
                 (if use-promicro-usb-hole?
                   (union (pro-micro-holder c)
                          (trrs-usb-holder-holder c))
                   (union (usb-holder fusb-holder-position c)
                          #_(pro-micro-holder c)))
                 (if use-trrs? (trrs-holder c) ()))
                ()))
       (if use-screw-inserts? (screw-insert-holes screw-placement c) ())
       (if-not use-external-holder?
         (union
          (if use-trrs? (trrs-holder-hole c) (rj9-space frj9-start c))
          (if use-promicro-usb-hole?
            (union (trrs-usb-holder-space c)
                   (trrs-usb-jack c))
            (usb-holder-hole fusb-holder-position c)))
         (external-holder-space c))))
     (translate [0 0 -60] (cube 350 350 120)))))

(defn model-left [c]
  (mirror [-1 0 0] (model-right c)))

(defn plate-right [c]
  (let [use-screw-inserts? (get c :configuration-use-screw-inserts?)
        screw-outers       (if use-screw-inserts?
                             (screw-insert-outers screw-placement c)
                             ())
        screw-inners       (if use-screw-inserts?
                             (translate [0 0 -2] (screw-insert-screw-holes screw-placement c))
                             ())
        bot                (cut (translate [0 0 -0.1] (union (case-walls c) screw-outers)))
        inner-thing        (difference (translate [0 0 -0.1] (project (union (extrude-linear {:height 5
                                                                                              :scale  0.1
                                                                                              :center true} bot)
                                                                             (cube 50 50 5))))
                                       screw-inners)]
    (difference (extrude-linear {:height 3} inner-thing)
                screw-inners)))

(defn plate-left [c]
  (mirror [-1 0 0] (plate-right c)))

(def c {:configuration-nrows                  5
        :configuration-ncols                  6
        :configuration-thumb-count            :six
        :configuration-last-row-count         :two
        :configuration-switch-type            :kailh
        :configuration-inner-column           :normie
        :configuration-hide-last-pinky?       false

        :configuration-alpha                  (/ pi 9)
        :configuration-pinky-alpha            (/ pi 9)
        :configuration-beta                   (/ pi 26)
        :configuration-centercol              4
        :configuration-tenting-angle          (/ pi 12)
        :configuration-rotate-x-angle         (/ pi 180)

        :configuration-use-promicro-usb-hole? false
        :configuration-use-trrs?              false
        :configuration-use-external-holder?   true

        :configuration-use-hotswap?           false
        :configuration-thumb-cluster-offset-x 6
        :configuration-thumb-cluster-offset-y -3
        :configuration-thumb-cluster-offset-z 7
        :configuration-custom-thumb-tenting?  false
        :configuration-custom-thumb-tenting-x (/ pi 0.5)
        :configuration-custom-thumb-tenting-y (/ pi 0.5)
        :configuration-custom-thumb-tenting-z (/ pi 0.5)
        :configuration-custom-thumb-offsets?  false
        :configuration-thumb-top-right-offset-x -12
        :configuration-thumb-top-right-offset-y -16
        :configuration-thumb-top-right-offset-z 3
        :configuration-stagger?               true
        :configuration-stagger-index          [0 0 0]
        :configuration-stagger-middle         [0 2.8 -2.5]
        :configuration-stagger-ring           [0 0 0]
        :configuration-stagger-pinky          [0 -13 2]
        :configuration-use-wide-pinky?        false
        :configuration-z-offset               8
        :configuration-use-wire-post?         false
        :configuration-use-screw-inserts?     true

        :configuration-show-caps?             false
        :configuration-plate-projection?      false})

(spit "things/right.scad"
      (write-scad (model-right c)))

#_(spit "things/right-plate.scad"
        (write-scad (plate-right c)))

#_(spit "things/right-plate.scad"
        (write-scad
         (cut
          (translate [0 0 -0.1]
                     (difference (union case-walls
                                        teensy-holder
                                          ; rj9-holder
                                        screw-insert-outers)
                                 (translate [0 0 -10] screw-insert-screw-holes))))))

#_(spit "things/left.scad"
        (write-scad (mirror [-1 0 0] model-right)))

#_(spit "things/left-plate.scad"
        (write-scad (plate-left c)))
        
#_(spit "things/right-test.scad"
        (write-scad
         (union
          key-holes
          connectors
          thumb
          thumb-connectors
          case-walls
          thumbcaps
          caps
          teensy-holder
          rj9-holder
          usb-holder-hole)))

#_(spit "things/test.scad"
        (write-scad
         (difference usb-holder usb-holder-hole)))

#_(defn -main [dum] 1)  ; dummy to make it easier to batc
