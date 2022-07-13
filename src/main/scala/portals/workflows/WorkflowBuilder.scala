package portals

trait WorkflowBuilder:
  private[portals] var tasks: Map[String, TaskBehavior[_, _]] = Map.empty
  private[portals] var connections: List[(String, String)] = List.empty
  private[portals] var sources: Map[String, TaskBehavior[_, _]] = Map.empty
  private[portals] var sinks: Map[String, TaskBehavior[_, _]] = Map.empty

  private[portals] var _task_id: Int = 0
  private[portals] def task_id(): String = {
    _task_id = _task_id + 1
    "$" + _task_id.toString
  }

  def build(): Workflow

  def source[T](): AtomicStream[Nothing, T]

  def from[I, O](wfb: AtomicStream[I, O]): AtomicStream[Nothing, O]

  def merge[I1, I2, O](wfb1: AtomicStream[I1, O], wfb2: AtomicStream[I2, O]): AtomicStream[Nothing, O]

  def cycle[T](): AtomicStream[T, T]

end WorkflowBuilder
