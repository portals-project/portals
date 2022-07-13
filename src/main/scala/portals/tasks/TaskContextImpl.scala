package portals

private[portals] class TaskContextImpl[I, O] extends TaskContext[I, O]:
  override val state: TaskState[Any, Any] = TaskState()

  override def emit(event: O): Unit = opr.submit(event)

  override def fuse(): Unit = opr.fuse()

  private lazy val _log = Logger(this.getClass().toString())
  override def log: Logger = _log

  private[portals] var key: Key[Int] = null

  private[portals] var system: SystemContext = null

  private[portals] var opr: OpRef[I, O] = null
