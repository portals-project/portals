package pods.workflows

object Systems:
  def local(): SystemContext = new LocalSystem

  class LocalSystem extends SystemContext:
    val executionContext: ExecutionContext = ExecutionContext()
    val registry: GlobalRegistry = GlobalRegistry()

    def launch(workflow: Workflow): Unit = 
      WorkflowRunner.run(workflow)(using this)

    def shutdown(): Unit = 
      Thread.sleep(500)
      executionContext.shutdown()