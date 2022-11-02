package portals.system.test

import portals.*

private[portals] class TestConnection(val connection: AtomicConnection[_])(using rctx: TestRuntimeContext)
