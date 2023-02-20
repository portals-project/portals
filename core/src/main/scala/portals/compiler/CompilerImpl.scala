package portals.compiler

import portals.*
import portals.compiler.phases.*
import portals.compiler.physicalplan.*

private[portals] class CompilerImpl extends Compiler[Application, PhysicalPlan[_]]:
  given ctx: CompilerContext = new CompilerContext()

  override def compile(application: Application): PhysicalPlan[_] =
    CompilerPhases.empty
      .andThen(CompilerPhases.wellFormedCheck)
      .andThen(CompilerPhases.codeGeneration)
      .run(application)

end CompilerImpl // class
