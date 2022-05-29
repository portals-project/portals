package pods.workflows

object Workflows:
  def builder(): WorkflowBuilder = new WorkflowBuilderImpl()
