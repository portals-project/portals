package portals.compiler

import portals.*
import portals.compiler.phases.*
import portals.compiler.physicalplan.*

private[portals] class PreCheckCompilerImpl extends Compiler[Application, Application]:
  given ctx: CompilerContext = new CompilerContext()

  override def compile(application: Application): Application =
    CompilerPhases.empty
      .andThen(CompilerPhases.wellFormedCheck)
      .run(application)

end PreCheckCompilerImpl // class
