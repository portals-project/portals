package pods.workflows

/** AttenuatedTaskContext, context without emit, send, etc. */
sealed trait AttenuatedTaskContext[I, O] {
  /** State of the task */
  def state: TaskState[Any, Any]

  /** Logger */
  def log: Logger
}

object AttenuatedTaskContext {
  def fromTaskContext[I, O](ctx: TaskContext[I, O]): AttenuatedTaskContext[I, O] =
    new AttenuatedTaskContext {
      def state = ctx.state
      def log = ctx.log
    }
}