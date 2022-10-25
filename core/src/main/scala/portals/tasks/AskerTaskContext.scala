package portals

trait AskerTaskContext[T, U, Req, Rep, Portals <: AtomicPortalRefType[Req, Rep]] extends TaskContext[T, U]:
  def ask(portal: Portals)(req: Req): Future[Rep]
  def await(future: Future[Rep])(f: Option[Rep] => Task[T, U]): Task[T, U]
