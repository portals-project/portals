package pods.workflows

// TODO: a workflow should consist of tasks, sources, and sinks, currently it 
// only consists of tasks.
class Workflow(
    private[pods] val name: String,
    private[pods] val tasks: Map[String, TaskBehavior[_, _]],
    private[pods] val sources: Map[String, TaskBehavior[_, _]],
    private[pods] val sinks: Map[String, TaskBehavior[_, _]],
    private[pods] val connections: List[(String, String)],
):
  override def toString(): String = 
    s"""Workflow(
    \t$name, 
    \t$tasks, 
    \t$connections,
    )"""