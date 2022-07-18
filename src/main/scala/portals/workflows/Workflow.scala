package portals

// TODO: a workflow should consist of tasks, sources, and sinks, currently it
// only consists of tasks.
class Workflow(
    private[portals] val name: String,
    private[portals] val tasks: Map[String, TaskBehavior[_, _]],
    private[portals] val sources: Map[String, TaskBehavior[_, _]],
    private[portals] val sinks: Map[String, TaskBehavior[_, _]],
    private[portals] val connections: List[(String, String)]
):
  override def toString(): String =
    s"""Workflow(
    \t$name, 
    \t$tasks, 
    \t$connections,
    )"""
