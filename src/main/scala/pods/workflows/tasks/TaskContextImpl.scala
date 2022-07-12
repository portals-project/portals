package pods.workflows

private[pods] class TaskContextImpl[I, O] extends TaskContext[I, O]:
  override val state: TaskState[Any, Any] = TaskState()

  override def emit(event: O): Unit = opr.submit(event)

  override def fuse(): Unit = opr.fuse()

  private lazy val _log = Logger(this.getClass().toString())
  override def log: Logger = _log

  override private[pods] var key: Key[Int] = null

  override private[pods] var system: SystemContext = null

  private[pods] var opr: OpRef[I, O] = null
