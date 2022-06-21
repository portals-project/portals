package pods.workflows

object ExecutionContexts:
  def local(): ExecutionContext = new LocalExecutionContext()