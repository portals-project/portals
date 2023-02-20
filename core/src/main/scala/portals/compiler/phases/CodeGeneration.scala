package portals.compiler.phases

import portals.*
import portals.compiler.*
import portals.compiler.physicalplan.*

private[portals] object CodeGeneration extends CompilerPhase[Application, PhysicalPlan[_]]:
  override def run(application: Application)(using ctx: CompilerContext): PhysicalPlan[_] =
    PhysicalPlanBuilder.fromApplication(application)

end CodeGeneration // object
