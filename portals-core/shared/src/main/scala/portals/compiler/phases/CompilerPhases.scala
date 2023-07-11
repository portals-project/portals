package portals.compiler.phases

import portals.compiler.phases.portal.*

private[portals] object CompilerPhases:
  def wellFormedCheck = WellFormedCheck
  def codeGeneration = CodeGeneration
  def rewritePortal = RewritePortalPhase
