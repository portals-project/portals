package pods.workflows

/** OStreamRef */
trait OStreamRef[T]:
  private[pods] def subscribe(subscriber: IStreamRef[T]): Unit
  
  // omitted: private[pods] def close(): Unit