package pods.workflows

/** ReducedTaskContext, context without emit, etc.
 * 
 *  To be used in Map, FlatMap, etc., where the task is not supposed to emit.
 */
sealed trait ReducedTaskContext[I, O] {
  /** State of the task */
  def state: TaskState[Any, Any]

  /** Logger */
  def log: Logger
}

object ReducedTaskContext {
  def fromTaskContext[I, O](ctx: TaskContext[I, O]): ReducedTaskContext[I, O] =
    new ReducedTaskContext {
      def state = ctx.state
      def log = ctx.log
    }
}