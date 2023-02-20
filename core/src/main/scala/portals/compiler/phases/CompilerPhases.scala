package portals.compiler.phases

object CompilerPhases:
  def empty = EmptyPhase
  def wellFormedCheck = WellFormedCheck
  def codeGeneration = CodeGeneration
