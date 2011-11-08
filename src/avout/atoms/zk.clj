(ns avout.atoms.zk
  (:use avout.atoms)
  (:require [zookeeper :as zk]
            [zookeeper.data :as data]
            [avout.util :as util]))

(deftype ZKAtomState [client dataNode]
  AtomState
  (getState [this]
    (let [{:keys [data stat]} (zk/data client dataNode)]
      (util/deserialize-form data)))

  (setState [this new-value] (zk/set-data client dataNode (util/serialize-form new-value) -1)))

(defn zk-atom
  ([client name init-value & {:keys [validator]}]
     (doto (distributed-atom client name (ZKAtomState. client (zk/create-all client (str name "/data"))))
       (set-validator! validator)
       (.reset init-value)))
  ([client name] ;; for connecting to an existing atom only
     (distributed-atom client name (ZKAtomState. client (zk/create-all client (str name "/data"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; zk-atom examples
(comment

  (use 'avout.atoms :reload-all)
  (use 'avout.atoms.zk :reload-all)
  (require '[zookeeper :as zk])

  (def client (zk/connect "127.0.0.1"))
  (def a0 (zk-atom client "/a1" 0))
  @a0
  (swap!! a0 inc)
  @a0

  (def a1 (zk-atom client "/a1" {}))
  @a1
  (swap!! a1 assoc :a 1)
  (swap!! a1 update-in [:a] inc)

  ;; check that reads are not blocked by writes
  (future (swap!! a1 (fn [v] (Thread/sleep 5000) (update-in v [:a] inc))))
  @a1

  ;; test watches
  (add-watch a1 :a1 (fn [akey aref old-val new-val] (println akey aref old-val new-val)))
  (swap!! a1 update-in [:a] inc)
  (swap!! a1 update-in [:a] inc)
  (remove-watch a1 :a1)
  (swap!! a1 update-in [:a] inc)

  )