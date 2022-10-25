package portals

trait GenericTaskContext[T, U]

private[portals] trait EmittingTaskContext[T, U] extends GenericTaskContext[T, U]:
  def emit(u: U): Unit

private[portals] trait StatefulTaskContext[T, U] extends GenericTaskContext[T, U]:
  def state: TaskState[Any, Any]

private[portals] trait LoggingTaskContext[T, U] extends GenericTaskContext[T, U]:
  def log: Logger
