package portals

sealed trait WrappedEvent[+T]
case class Event[T](key: Key[Int], event: T) extends WrappedEvent[T]
case class Error[T](t: Throwable) extends WrappedEvent[T]
case object Atom extends WrappedEvent[Nothing]
case object Seal extends WrappedEvent[Nothing]
