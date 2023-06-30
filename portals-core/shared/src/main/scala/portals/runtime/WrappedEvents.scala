package portals.runtime

import portals.util.Key

// Warning: if WrappedEvents is private[portals] then the tests will fail, not sure why
object WrappedEvents:
  private[portals] sealed trait WrappedEvent[+T]
  private[portals] case class Event[T](key: Key, event: T) extends WrappedEvent[T]
  private[portals] case class Error[T](t: Throwable) extends WrappedEvent[T]
  private[portals] case object Atom extends WrappedEvent[Nothing]
  private[portals] case object Seal extends WrappedEvent[Nothing]

  /** internal API */
  private[portals] case class PortalMeta(
      portal: String,
      askingTask: String,
      askingKey: Key,
      id: Int, // request id
      askingWF: String,
  )

  private[portals] case class Ask[T](
      key: Key,
      meta: PortalMeta,
      event: T
  ) extends WrappedEvent[T]

  /** internal API */
  private[portals] case class Reply[T](
      key: Key,
      meta: PortalMeta,
      event: T
  ) extends WrappedEvent[T]

  extension (event: WrappedEvent[_])
    def key: Key = event match
      case Event(key, _) => key
      case Ask(key, _, _) => key
      case Reply(key, _, _) => key
      case Error(_) => Key(-1)
      case Atom => Key(-1)
      case Seal => Key(-1)
