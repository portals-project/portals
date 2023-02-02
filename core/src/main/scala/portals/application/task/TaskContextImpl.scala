package portals

private[portals] class TaskContextImpl[T, U, Req, Rep]
    extends TaskContext[T, U, Req, Rep]
    with MapTaskContext[T, U]
    with ProcessorTaskContext[T, U]
    with AskerTaskContext[T, U, Req, Rep]
    with ReplierTaskContext[T, U, Req, Rep]:

  //////////////////////////////////////////////////////////////////////////////
  // ProcessorTaskContext
  //////////////////////////////////////////////////////////////////////////////
  override val state: TaskState[Any, Any] = TaskState()

  override def emit(event: U): Unit = outputCollector.submit(Event(key, event))

  private lazy val _log = Logger(path)

  override def log: Logger = _log

  /** should be var so that it can be swapped out during runtime */
  private[portals] var path: String = "" // TODO: make this set by the runtime
  private[portals] var key: Key[Long] = Key(-1) // TODO: make this set by the runtime
  private[portals] var system: PortalsSystem = _
  private[portals] var outputCollector: OutputCollector[T, U, Any, Any] = _
  private[portals] var task: GenericTask[T, U, Req, Rep] = _

  //////////////////////////////////////////////////////////////////////////////
  // AskerTaskContext
  //////////////////////////////////////////////////////////////////////////////
  private lazy val _continuations =
    PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]]("continuations", Map.empty)(using this)

  override def ask(portal: AtomicPortalRefKind[Req, Rep])(msg: Req): Future[Rep] =
    val future: Future[Rep] = Future()
    outputCollector.ask(portal.path, this.path, msg, this.key, future.id)
    future

  override def await(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Unit): Unit =
    _continuations.update(future.asInstanceOf[FutureImpl[_]].id, f)

  //////////////////////////////////////////////////////////////////////////////
  // ReplierTaskContext
  //////////////////////////////////////////////////////////////////////////////
  /** should be var so that it can be swapped out during runtime */
  private[portals] var id: Int = _
  private[portals] var asker: String = _
  private[portals] var portal: String = _
  private[portals] var portalAsker: String = _

  override def reply(msg: Rep): Unit =
    outputCollector.reply(msg, this.portal, this.asker, this.key, this.id)
end TaskContextImpl // class