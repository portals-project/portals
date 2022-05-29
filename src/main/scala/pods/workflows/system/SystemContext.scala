package pods.workflows

trait SystemContext:
  val executionContext: ExecutionContext
  val registry: GlobalRegistry

  def launch(workflow: Workflow): Unit

  def shutdown(): Unit
