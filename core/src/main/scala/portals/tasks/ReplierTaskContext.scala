package portals

trait ReplierTaskContext[T, U, Req, Rep, Portals <: AtomicPortalRefType[Req, Rep]] extends TaskContext[T, U]:
  def reply(r: Rep): Unit
