package portals.compiler

import portals.*
import portals.compiler.phases.*
import portals.compiler.physicalplan.*

/** Compiler that performs all semantic checks before transforming the code to
  * its physical representation.
  */
private[portals] class PreCheckCompilerImpl extends Compiler[Application, Application]:
  given ctx: CompilerContext = new CompilerContext()

  override def compile(application: Application): Application =
    CompilerPhases.empty
      .andThen(CompilerPhases.wellFormedCheck)
      .run(application)

end PreCheckCompilerImpl // class
