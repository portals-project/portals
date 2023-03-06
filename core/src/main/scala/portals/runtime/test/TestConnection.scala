package portals

import portals.*
import portals.application.AtomicConnection

/** Internal API. Wraps around an atomic connection. */
private[portals] class TestConnection(val connection: AtomicConnection[_])(using rctx: TestRuntimeContext)
