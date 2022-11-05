package portals

/** TaskContext */
private[portals] trait TaskContext[T, U]
    extends GenericTaskContext[T, U]
    with EmittingTaskContext[T, U]
    with StatefulTaskContext[T, U]
    with LoggingTaskContext[T, U]:
  //////////////////////////////////////////////////////////////////////////////
  // Base Operations
  //////////////////////////////////////////////////////////////////////////////

  /** State of the task, scoped by the contextual invocation context */
  def state: TaskState[Any, Any]

  /** Emit an event */
  def emit(event: U): Unit

  // deprecated
  // /** Finishes the ongoing atom and starts a new tick */
  // private[portals] def fuse(): Unit // or tick()

  /** Logger */
  def log: Logger

  //////////////////////////////////////////////////////////////////////////////
  // Execution Context
  //////////////////////////////////////////////////////////////////////////////

  /** The Path of this task */
  // has to be var so that it can be swapped at runtime
  private[portals] var path: String

  /** Contextual key for per-key execution */
  // has to be var so that it can be swapped at runtime
  private[portals] var key: Key[Int]

  /** The `SystemContext` that this task belongs to */
  // has to be var so that it can be swapped at runtime
  private[portals] var system: PortalsSystem

object TaskContext:
  def apply[T, U](): TaskContextImpl[T, U] = new TaskContextImpl[T, U]
