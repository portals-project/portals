package portals

sealed trait WrappedEvents[T]
case class Event[T](event: T) extends WrappedEvents[T]
case class Atom[T]() extends WrappedEvents[T]