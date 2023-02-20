package portals.compiler.phases

private[portals] object CompilerPhases:
  def empty = EmptyPhase
  def wellFormedCheck = WellFormedCheck
  def codeGeneration = CodeGeneration
