package portals

trait Future[+T]:
  def value: Option[T]

  override def toString(): String = "Future(" + value.toString() + ")"

private[portals] class FutureImpl[T](id: Int) extends Future[T]:
  private[portals] val _id: Int = id
  private[portals] var _value: Option[T] = None
  def value: Option[T] = _value

private[portals] object Future:
  def apply[T](id: Int): Future[T] = new FutureImpl[T](id)
