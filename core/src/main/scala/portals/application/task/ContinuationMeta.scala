package portals.application.task

import portals.util.Key

case class ContinuationMeta(
    id: Int,
    asker: String,
    portal: String,
    portalAsker: String,
    askerKey: Key[Long],
)
