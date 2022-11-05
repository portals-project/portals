package portals

trait AskerTaskContext[T, U, Req, Rep] extends TaskContext[T, U]:
  def ask(portal: AtomicPortalRefType[Req, Rep])(req: Req): Future[Rep]
  def await(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Task[T, U]): Task[T, U]

// object AskerTaskContext:
//   // this seems wrong :/
//   def fromTaskContext[T, U, Req, Rep](
//       ctx: TaskContext[T | portals.PortalsExtension.Reply[Rep], U | portals.PortalsExtension.Ask[Req]]
//   ) =
//     new AskerTaskContext[T, U, Req, Rep] {
//       var id: Int = 0

//       override def ask(portal: AtomicPortalRefKind[Req, Rep])(req: Req): Future[Rep] =
//         id = (id % Int.MaxValue) + 1
//         ctx.emit(Ask(id, req))
//         ???

//       override def await(future: Future[Rep])(f: => Task[T, U]): Task[T, U] = ???
//       override def emit(event: U): Unit = ctx.emit(event)
//       override def fuse(): Unit = ctx.fuse()
//       var key: Key[Int] = ctx.key
//       override def log: Logger = ctx.log
//       var path: String = ctx.path
//       override def state: TaskState[Any, Any] = ctx.state
//       var system: SystemContext = ctx.system
//     }
