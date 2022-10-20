package portals

trait Future[T]:
  def value: Option[T]
