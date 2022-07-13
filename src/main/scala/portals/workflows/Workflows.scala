package portals

object Workflows:
  class UnNamedWorkflowBuilder:
    def withName(name: String): WorkflowBuilder = new WorkflowBuilderImpl(name)

  def builder(): UnNamedWorkflowBuilder = new UnNamedWorkflowBuilder()
