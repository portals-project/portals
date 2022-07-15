package portals

object Portals:
  def builder(name: String): WorkflowBuilder = new WorkflowBuilderImpl(name)
