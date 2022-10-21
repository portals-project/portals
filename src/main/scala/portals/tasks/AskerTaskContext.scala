package portals

trait AskerTaskContext[T, U, Req, Rep] extends TaskContext[T, U]:
  def ask(portal: AtomicPortalRefType[Req, Rep])(req: Req): Future[Rep]
  def await(future: Future[Rep])(f: => Task[T, U]): Task[T, U]
