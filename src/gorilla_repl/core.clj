;;;; This file is part of gorilla-repl. Copyright (C) 2014-, Jony Hudson.
;;;;
;;;; gorilla-repl is licenced to you under the MIT licence. See the file LICENCE.txt for full details.

(ns gorilla-repl.core
  (:require
   [org.httpkit.server :as server]
   [gorilla-repl.nrepl :as nrepl]
   [gorilla-repl.websocket-relay :as ws-relay]
   [gorilla-repl.renderer :as renderer] ;; this is needed to bring the render implementations into scope
   [gorilla-repl.version :as version]
   [gorilla-repl.handle :as handle]
   [clojure.set :as set]
   [clojure.java.io :as io]
   [reitit.ring :as ring])
  (:gen-class))

;; the combined routes - we serve up everything in the "public" directory of resources under "/".
;; The REPL traffic is handled in the websocket-transport ns.
(def router
  (ring/router
   [["/load" {:get (handle/wrap-api-handler handle/load-worksheet)}]
    ["/save" {:post (handle/wrap-api-handler handle/save)}]
    ["/gorilla-files" {:get (handle/wrap-api-handler handle/gorilla-files)}]
    ["/config" {:get (handle/wrap-api-handler handle/config)}]
    ["/repl" {:get ws-relay/ring-handler}]]))

(def reitit-app (ring/ring-handler router
                                   (ring/routes
                                    (ring/create-resource-handler
                                     {:path "/" :root "gorilla-repl-client"}))))

(defn run-gorilla-server
  [conf]
  ;; get configuration information from parameters
  (let [version (or (:version conf) "develop")
        webapp-requested-port (or (:port conf) 0)
        ip (or (:ip conf) "127.0.0.1")
        nrepl-requested-port (or (:nrepl-port conf) 0)  ;; auto-select port if none requested
        nrepl-port-file (io/file (or (:nrepl-port-file conf) ".nrepl-port"))
        gorilla-port-file (io/file (or (:gorilla-port-file conf) ".gorilla-port"))
        project (or (:project conf) "no project")
        keymap (or (:keymap (:gorilla-options conf)) {})
        phone-home (or (:phone-home (:gorilla-options conf)) (nil? (:phone-home (:gorilla-options conf))))
        _ (handle/update-excludes (fn [x] (set/union x (:load-scan-exclude (:gorilla-options conf)))))]
    ;; app startup
    (println "Gorilla-REPL:" version)
    ;; build config information for client
    (handle/set-config :project project)
    (handle/set-config :keymap keymap)
    ;; check for updates
    (if phone-home
      (version/check-for-update version)
      nil)  ;; runs asynchronously)
    ;; first startup nREPL
    (nrepl/start-and-connect nrepl-requested-port nrepl-port-file)
    ;; and then the webserver
    (let [s (server/run-server #'reitit-app {:port webapp-requested-port :join? false :ip ip :max-body 500000000})
          webapp-port (:local-port (meta s))]
      (spit (doto gorilla-port-file .deleteOnExit) webapp-port)
      (println (str "Running at http://" ip ":" webapp-port "/worksheet.html ."))
      (println "Ctrl+C to exit."))))

(defn -main
  [& args]
  (run-gorilla-server {:port 8990}))
