package portals

private[portals] class TaskContextImpl[I, O] extends TaskContext[I, O]:
  override val state: TaskState[Any, Any] = TaskState()

  override def emit(event: O): Unit = cb.submit(key, event)

  override private[portals] def fuse(): Unit = cb.fuse()

  private lazy val _log = Logger(this.getClass().toString())
  override def log: Logger = _log

  private[portals] var path: String = "" // TODO: make this set by the runtime

  private[portals] var key: Key[Int] = Key(-1) // TODO: make this set by the runtime

  private[portals] var system: SystemContext = null

  private[portals] var cb: TaskCallback[I, O] = null
