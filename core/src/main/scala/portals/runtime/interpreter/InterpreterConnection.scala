package portals.runtime.interpreter

import portals.application.AtomicConnection
import portals.runtime.interpreter.InterpreterRuntimeContext

/** Internal API. Wraps around an atomic connection. */
private[portals] class InterpreterConnection(val connection: AtomicConnection[_])(using rctx: InterpreterRuntimeContext)
