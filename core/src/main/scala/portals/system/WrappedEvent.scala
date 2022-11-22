package portals

sealed trait WrappedEvent[+T]
case class Event[T](key: Key[Int], event: T) extends WrappedEvent[T]
case class Error[T](t: Throwable) extends WrappedEvent[T]
case object Atom extends WrappedEvent[Nothing]
case object Seal extends WrappedEvent[Nothing]

/** internal API */
private[portals] case class Ask[T](
    key: Key[Int],
    portal: String,
    portalAsker: String,
    replier: String,
    asker: String,
    id: Int,
    event: T
) extends WrappedEvent[T]

/** internal API */
private[portals] case class Reply[T](
    key: Key[Int],
    portal: String,
    portalAsker: String,
    replier: String,
    asker: String,
    id: Int,
    event: T
) extends WrappedEvent[T]
