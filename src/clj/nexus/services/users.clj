(ns nexus.services.users
  (:require
   [integrant.core :as ig]
   [nexus.db :as db]))

(defn create-user [db {:keys [first-name last-name middle-name email]}]
  (db/exec-one! db {:insert-into [:nexus.users]
                    :values [{:first_name first-name
                              :last_name last-name
                              :middle_name middle-name
                              :email email}]}))

(defn find-user-by-email [db email]
  (db/exec-one! db {:select [:*]
                    :from :nexus.users
                    :where [:= :email email]}))

(defn find-users-where [db where]
  (db/exec! db {:select [:*]
                :from :nexus.users
                :where where}))

(defmethod ig/init-key :nexus.services/users [_ {:keys [deps]}]
  (let [{:keys [db]} deps]
    {:create (partial create-user db)
     :find-by-email (partial find-user-by-email db)
     :find-where (partial find-users-where db)}))
