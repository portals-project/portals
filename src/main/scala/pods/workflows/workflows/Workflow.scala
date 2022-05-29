package pods.workflows

// TODO: a workflow should consist of tasks, sources, and sinks, currently it 
// only consists of tasks.
class Workflow(
  private[pods] val name: String,
  private[pods] val tasks: List[(String, TaskBehavior[_, _])],
  // private[pods] val sources: List[String],
  // private[pods] val sinks: List[String],
  private[pods] val connections: List[(String, String)],
)
