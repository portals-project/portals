package pods.workflows

/** TaskContext */
private[pods] trait TaskContext[I, O]:
  //////////////////////////////////////////////////////////////////////////////
  // Base Operations
  //////////////////////////////////////////////////////////////////////////////

  /** State of the task */
  def state: TaskState[Any, Any]

  /** Emit an event */
  def emit(event: O): Unit

  /** finishes the ongoing atom and starts a new tick */
  def fuse(): Unit // or tick()

  /** Logger */
  def log: Logger

  //////////////////////////////////////////////////////////////////////////////
  // Execution Context
  //////////////////////////////////////////////////////////////////////////////

  /** Contextual key for per-key execution */
  // has to be var so that it can be swapped at runtime
  private[pods] var key: Key[Int]

  /** The `SystemContext` that this task belongs to */
  // has to be var so that it can be swapped at runtime
  private[pods] var system: SystemContext

object TaskContext:
  def apply[I, O](): TaskContextImpl[I, O] = new TaskContextImpl[I, O]
  // def syncLocal[I, O](): LocalTaskContextImpl[I, O] = new LocalTaskContextImpl[I, O]