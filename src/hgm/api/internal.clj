(ns hgm.api.internal
  (:require [hgm.db.core :as db]))


(defn update-goal-stats
  [stats player event]
  (if (= (:id player) (:player event))
    (update-in stats [:goals] inc)
    stats))

(defn update-plus-minus-stats
  [stats player event]
  (if (:on-ice stats)
    (if (= (:team player) (:team event))
      (update-in stats [:plus-minus] inc)
      (update-in stats [:plus-minus] dec))
    stats))

(defn exit-ice
  [stats player event]
   (if (= (:player event) (:id player))
     (-> stats
         (assoc :on-ice false)
         (update-in [:time-on-ice] #(+ % (- (:time event) (:last-entered stats)))))
     stats))

(defn enter-ice
  [stats player event]
  (if (= (:player event) (:id player))
    (-> stats
        (assoc :on-ice true)
        (assoc :last-entered (:time event)))
    stats))

(defn compute-player-stats
  [player events]
  (let [stats (reduce
               (fn [stats event]
                 (case (:type event)
                   :goal (-> stats
                             (update-goal-stats player event)
                             (update-plus-minus-stats player event))
                   :enter-ice (enter-ice stats player event)
                   :exit-ice (exit-ice stats player event)
                   ))
               {:on-ice false
                :goals 0
                :plus-minus 0
                :time-on-ice 0}
               events)]
    (dissoc stats :on-ice :last-entered)))

(defn get-player-stats-internal
  ([player]
     (db/get-player-career-stats player))
  ([player game]
     (if (db/unarchived-game-exists? game)
       (let [events (db/get-game-events game)]
         (compute-player-stats (db/get-player player) events))
       (db/get-player-game-stats player game))))
