package pods.workflows

sealed trait Event[T]
case class EventWithId[T](id: Int, event: T) extends Event[T]
