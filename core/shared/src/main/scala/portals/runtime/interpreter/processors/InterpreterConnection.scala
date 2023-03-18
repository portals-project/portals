package portals.runtime.interpreter.processors

import portals.application.AtomicConnection

/** Internal API. Wraps around an atomic connection. */
private[portals] class InterpreterConnection(val connection: AtomicConnection[_])
