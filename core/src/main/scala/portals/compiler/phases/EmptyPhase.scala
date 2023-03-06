package portals.compiler.phases

import portals.*
import portals.application.Application
import portals.compiler.*

/** Empty compiler phase, can be used to start a chain of compiler phases. */
private[portals] object EmptyPhase extends CompilerPhase[Application, Application]:
  override def run(application: Application)(using CompilerContext): Application =
    application
