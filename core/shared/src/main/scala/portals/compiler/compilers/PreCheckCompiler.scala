package portals.compiler.compilers

import portals.application.Application
import portals.compiler.*
import portals.compiler.phases.*
import portals.compiler.physicalplan.*

/** Compiler that performs all semantic checks before transforming the code to
  * its physical representation.
  */
private[portals] class PreCheckCompiler extends Compiler[Application, Application]:
  given ctx: CompilerContext = new CompilerContext()

  override def compile(application: Application): Application =
    CompilerPhase.empty
      .andThen(CompilerPhases.wellFormedCheck)
      .run(application)

end PreCheckCompiler // class
