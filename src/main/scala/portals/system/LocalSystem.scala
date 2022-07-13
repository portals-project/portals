package portals

class LocalSystem extends SystemContext:
  val executionContext: ExecutionContext = ExecutionContexts.local()
  val registry: GlobalRegistry = GlobalRegistry()

  def launch(workflow: Workflow): Unit = 
    WorkflowRunner.run(workflow)(using this)

  def shutdown(): Unit = 
    executionContext.shutdown()
