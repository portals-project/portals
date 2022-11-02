package portals

trait Future[+T]:
  def value: Option[T]

private[portals] class FutureImpl[T](id: Int) extends Future[T]:
  private[portals] val _id: Int = id
  private[portals] var _value: Option[T] = None
  def value: Option[T] = _value
