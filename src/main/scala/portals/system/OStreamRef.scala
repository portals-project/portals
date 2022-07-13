package portals

/** OStreamRef */
trait OStreamRef[T]:
  private[portals] val opr: OpRef[_, T]

  private[portals] def subscribe(subscriber: IStreamRef[T]): Unit
  
  // omitted: private[portals] def close(): Unit