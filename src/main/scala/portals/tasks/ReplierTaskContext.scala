package portals

trait ReplierTaskContext[T, U, Req, Rep] extends TaskContext[T, U]:
  def reply(r: Rep): Unit
