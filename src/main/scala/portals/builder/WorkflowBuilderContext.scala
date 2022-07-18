package portals

trait WorkflowBuilderContext[T, U](using val bctx: BuilderContext):
  val name: String

  var tasks: Map[String, Task[_, _]] = Map.empty
  var connections: List[(String, String)] = List.empty
  var sources: Map[String, Task[T, _]] = Map.empty
  var sinks: Map[String, Task[_, U]] = Map.empty

  var _next_task_id: Int = 0
  def next_task_id(): String =
    _next_task_id = _next_task_id + 1
    "$" + _next_task_id.toString
