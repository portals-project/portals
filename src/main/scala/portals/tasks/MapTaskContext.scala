package portals

/** MapTaskContext, context without emit, etc.
  *
  * To be used in Map, FlatMap, etc., where the task is not supposed to emit.
  */
sealed trait MapTaskContext[T, U]
    extends GenericTaskContext[T, U]
    with StatefulTaskContext[T, U]
    with LoggingTaskContext[T, U]:
  /** State of the task */
  def state: TaskState[Any, Any]

  /** Logger */
  def log: Logger

object MapTaskContext {
  def fromTaskContext[T, U](ctx: TaskContext[T, U]): MapTaskContext[T, U] =
    new MapTaskContext {
      def state = ctx.state
      def log = ctx.log
    }
}
