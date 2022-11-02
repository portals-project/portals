package portals.system.test

import portals.AtomicConnection

class TestConnection(val connection: AtomicConnection[_])(using rctx: TestRuntimeContext)