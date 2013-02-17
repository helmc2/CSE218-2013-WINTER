(ns hgm.core
  (:use compojure.core
        ring.middleware.json-response
        ring.util.response
        ring.adapter.jetty
        [clojure.core.incubator :only (-?>)]
        [environ.core :only [env]])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [clojure.java.io :as io]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds]
                             [openid :as openid])
            [hgm.api.core :as api]
            [hgm.db.core :as db]))


(defroutes hgm-routes
  (GET  "/" request
        (io/file
         (if (friend/authorized? #{:admin} friend/*identity*)
           "resources/controlPanel.html"
           (if (friend/authorized? #{:official} friend/*identity*)
             "resources/hockey.html"
             "resources/login.html"))))

  (GET  "/teams/:team/get-forwards" [team] (api/get-forwards-names team))
  (GET  "/teams/:team/get-defense"  [team] (api/get-defenders-names  team))
  (GET  "/teams/:team/get-goalies"  [team] (api/get-goalies-names  team))
  (GET  "/teams/:team/get-roster"   [team] (api/get-roster-names   team))

  ;; FIXME: /users should be admin-only, but we'll wait until we have a proper db
  (GET  "/users"                    []     (api/get-users))
  (PUT  "/users/:user"              [user roles]
        (friend/authorize #{:admin}
           (api/update-user user roles)))

  (POST "/events/start-game"        [home away]
        (friend/authorize #{:official}
           (api/add-start-game-event home away)))

  (POST "/events/swap-players"      [gameId time outPlayer inPlayer]
        (friend/authorize #{:official}
           (api/add-swap-players-event gameId time outPlayer inPlayer)))

  (POST "/events/end-game"          [gameId time]
        (friend/authorize #{:official}
           (api/add-end-game-event gameId time)))

  (POST "/events/shot"              [gameId time player]
        (friend/authorize #{:official}
           (api/add-shot-event gameId time player)))


  (GET "/login" request (io/file "resources/login.html"))
  (friend/logout (ANY "/logout" request (redirect "/")))

  ;; FIXME: /view-openid and /echo-roles are only here for debugging purposes
  (GET "/view-openid" request
       (str "OpenId authentication? " (-?> request friend/identity friend/current-authentication pr-str)))
  (GET "/echo-roles" request (friend/authenticated
                              (-> (friend/current-authentication request)
                                  (select-keys [:roles])
                                  str)))
  (route/resources "/")
  (route/not-found "Page not found"))


(defn check-creds
  [m]
  (if-let [user (db/get-user (:identity m))]
    (merge m user)
    (merge m (db/create-user m))))

(def app
  (-> hgm-routes
      (friend/authenticate {:workflows [(openid/workflow)]
                            :credential-fn check-creds})
      handler/site
      wrap-json-response))

(defn -main [& args]
  (let [port (Integer. (or (env :port) 8000))]
    (run-jetty app {:port port})))

