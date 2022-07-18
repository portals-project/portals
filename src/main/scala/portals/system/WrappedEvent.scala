package portals

sealed trait WrappedEvent[T]
case class Event[T](event: T) extends WrappedEvent[T]
case class Atom[T]() extends WrappedEvent[T]
