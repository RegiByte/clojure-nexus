(ns nexus.users.queries
  "Pure HoneySQL query builders for users domain.
   All functions return HoneySQL data structures that can be tested in isolation.")

;; ============================================================================
;; Query Builders
;; ============================================================================

(def public-fields [:id
                    :first_name
                    :last_name
                    :middle_name
                    :email
                    :created_at
                    :updated_at])

(defn not-deleted []
  [:= nil :deleted_at])

(defn find-by-email-query
  "Build query to find user by email"
  [email]
  {:select [:*]
   :from [:nexus.users]
   :where [:and
           [:= :email email]
           (not-deleted)]})

(defn find-by-id-query
  "Build query to find user by id"
  [id]
  {:select [:*]
   :from [:nexus.users]
   :where [:and
           [:= :id id]
           (not-deleted)]})

(defn insert-user-query
  "Build query to insert a new user"
  [{:keys [first-name last-name middle-name email password-hash]}]
  {:insert-into [:nexus.users]
   :values [{:first_name first-name
             :last_name last-name
             :middle_name middle-name
             :email email
             :password_hash password-hash}]
   :returning public-fields})

(defn list-users-query
  "Build query to list users with pagination"
  [{:keys [limit offset] :or {limit 50 offset 0}}]
  {:select [:id
            :email
            :first_name
            :last_name
            :middle_name
            :created_at
            :updated_at]
   :from [:nexus.users]
   :where (not-deleted)
   :order-by [[:created_at :desc]]
   :limit limit
   :offset offset})


(defn update-user-query
  "Build query to update user fields"
  [id updates]
  {:update :nexus.users
   :set (into {} (map (fn [[k v]] [(keyword (name k)) v]) updates))
   :where [:= :id id]
   :returning [:*]})

(defn soft-delete-user-query
  "Build query to soft delete a user"
  [id]
  {:update :nexus.users
   :set {:deleted_at [:now]}
   :where [:= :id id]
   :returning [:*]})

(defn update-password-query
  "Build query to update user password"
  [user-id password-hash]
  {:update :nexus.users
   :set {:password_hash password-hash}
   :where [:= :id user-id]
   :returning [:id :email]})

(defn search-users-query
  "Build query to search users by name or email"
  [search-term]
  {:select [:*]
   :from [:nexus.users]
   :where [:and
           (not-deleted)
           [:or
            [:ilike :first_name (str "%" search-term "%")]
            [:ilike :last_name (str "%" search-term "%")]
            [:ilike :email (str "%" search-term "%")]]]})
