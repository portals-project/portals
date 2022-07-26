package portals

sealed trait WrappedEvent[T]
case class Event[T](key: Key[Int], event: T) extends WrappedEvent[T]
case class Atom[T]() extends WrappedEvent[T]
