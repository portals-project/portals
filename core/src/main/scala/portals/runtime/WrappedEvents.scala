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
