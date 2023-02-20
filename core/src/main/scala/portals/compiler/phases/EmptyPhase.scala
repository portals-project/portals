package portals.compiler.phases

import portals.*
import portals.compiler.*

object EmptyPhase extends CompilerPhase[Application, Application]:
  override def run(application: Application)(using CompilerContext): Application =
    application
