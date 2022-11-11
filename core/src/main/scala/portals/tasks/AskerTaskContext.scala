package portals

private[portals] type Continuation[T, U, Req, Rep] = AskerTaskContext[T, U, Req, Rep] ?=> Task[T, U]

trait AskerTaskContext[T, U, Req, Rep] extends TaskContext[T, U]:
  def ask(portal: AtomicPortalRefType[Req, Rep])(req: Req): Future[Rep]
  def await(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Task[T, U]): Task[T, U]

object AskerTaskContext:
  def fromTaskContext[T, U, Req, Rep](
      ctx: TaskContext[T, U]
  )(portalcb: PortalTaskCallback[T, U, Req, Rep]): AskerTaskContext[T, U, Req, Rep] =
    new AskerTaskContext[T, U, Req, Rep] {
      private[portals] var _continuations = Map.empty[Int, Continuation[T, U, Req, Rep]]

      private var _id: Int = 0
      def id: Int = { _id += 1; _id }

      // AskerContext
      override def ask(portal: AtomicPortalRefKind[Req, Rep])(req: Req): Future[Rep] =
        val i = id
        portalcb.ask(portal)(req)(ctx.key, i)
        Future(i)
      override def await(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Task[T, U]): Task[T, U] =
        println(future)
        _continuations = _continuations + (future.asInstanceOf[FutureImpl[_]]._id -> f)
        Tasks.same

      // TaskContext
      override def emit(event: U): Unit = ctx.emit(event)
      var key: Key[Int] = ctx.key
      override def log: Logger = ctx.log
      var path: String = ctx.path
      override def state: TaskState[Any, Any] = ctx.state
      var system: PortalsSystem = ctx.system
    }
