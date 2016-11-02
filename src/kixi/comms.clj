(ns kixi.comms)

(defprotocol Communications
  (send-event!
    [this event version payload]
    [this event version payload command-id])
  (send-command!
    [this command version payload]
    [this command version payload opts])
  (attach-event-handler!
    [this group-id handler]
    [this group-id event version handler])
  (attach-command-handler!
    [this group-id event version handler]))
