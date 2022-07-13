package portals

object ExecutionContexts:
  def local(): ExecutionContext = new LocalExecutionContext()