package portals.system.test

import portals.*

/** Internal API. Wraps around an atomic connection. */
private[portals] class TestConnection(val connection: AtomicConnection[_])(using rctx: TestRuntimeContext)
