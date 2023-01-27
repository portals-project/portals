package portals

private[portals] class TaskContextImpl[I, O] extends TaskContext[I, O]:
  override val state: TaskState[Any, Any] = TaskState()

  override def emit(event: O): Unit = cb.submit(Event(key, event))

  private lazy val _log = Logger(path)
  override inline def log: Logger = _log

  private[portals] var path: String = "" // TODO: make this set by the runtime

  private[portals] var key: Key[Long] = Key(-1) // TODO: make this set by the runtime

  private[portals] var system: PortalsSystem = _

  private[portals] var cb: TaskCallback[I, O, Any, Any] = _

  private[portals] var task: Task[I, O] = _
