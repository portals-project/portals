package portals

sealed trait WrappedEvent[+T]
case class Event[T](key: Key[Long], event: T) extends WrappedEvent[T]
case class Error[T](t: Throwable) extends WrappedEvent[T]
case object Atom extends WrappedEvent[Nothing]
case object Seal extends WrappedEvent[Nothing]

/** internal API */
private[portals] case class PortalMeta(
    portal: String,
    askingTask: String,
    id: Int, // request id
)

private[portals] case class Ask[T](
    key: Key[Long],
    meta: PortalMeta,
    event: T
) extends WrappedEvent[T]

/** internal API */
private[portals] case class Reply[T](
    key: Key[Long],
    meta: PortalMeta,
    event: T
) extends WrappedEvent[T]
