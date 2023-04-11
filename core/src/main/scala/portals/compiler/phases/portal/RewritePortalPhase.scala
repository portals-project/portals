package portals.compiler.phases.portal

import portals.application.*
import portals.application.task.*
import portals.compiler.*
import portals.compiler.physicalplan.*
import portals.util.Common.Types.*

private[portals] object RewritePortalPhase extends CompilerPhase[Application, Application]:
  import RewritePortalHelpers.*

  override def run(application: Application)(using ctx: CompilerContext) =
    CompilerSubPhase.empty
      // NOTE: RewritePortalsSubPhase must be run before
      // RewriteWorkflowsSubPhase. This is due to the subsequent phase erasing
      // the portal dependency information in the workflows.
      .andThen(RewritePortalsSubPhase)
      .andThen(RewriteWorkflowsSubPhase)
      .run(application)

end RewritePortalPhase // object
