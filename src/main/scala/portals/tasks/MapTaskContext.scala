package portals

/** MapTaskContext, context without emit, etc. To be used in Map, FlatMap, etc., where the task is not supposed to emit.
  * The internal context `_ctx` should be swapped at runtime, as the runtime may swap the context.
  */
sealed trait MapTaskContext[T, U]
    extends GenericTaskContext[T, U]
    with StatefulTaskContext[T, U]
    with LoggingTaskContext[T, U]:
  /** State of the task */
  def state: TaskState[Any, Any]

  /** Logger */
  def log: Logger

  /** Internal Context */
  private[portals] var _ctx: TaskContext[T, U]

object MapTaskContext {
  def fromTaskContext[T, U](ctx: TaskContext[T, U]): MapTaskContext[T, U] =
    new MapTaskContext {
      override def state = _ctx.state

      override def log = _ctx.log

      private[portals] var _ctx: TaskContext[T, U] = ctx
    }
}
