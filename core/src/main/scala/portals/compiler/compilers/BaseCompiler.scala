package portals.compiler.compilers

import portals.application.Application
import portals.compiler.*
import portals.compiler.phases.*
import portals.compiler.physicalplan.*

/** Compiler that will run all checks and transform the code to its physical
  * representation.
  */
private[portals] class BaseCompiler extends Compiler[Application, PhysicalPlan[_]]:
  given ctx: CompilerContext = new CompilerContext()

  override def compile(application: Application): PhysicalPlan[_] =
    CompilerPhase.empty
      .andThen(CompilerPhases.wellFormedCheck)
      .andThen(CompilerPhases.codeGeneration)
      .run(application)

end BaseCompiler // class
