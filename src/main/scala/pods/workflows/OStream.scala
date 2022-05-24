package pods.workflows

private[pods] sealed trait OStream[T]:
  private[pods] val worker: Worker[T, T]
  private[pods] def subscribe(s: IStream[T]): Unit
  private[pods] def submit(event: T): Int
  def close(): Unit

private[pods] class OChannelImpl[T] extends OStream[T]:
  import Workers.*
  private[pods] val worker = Workers[T, T]()
    .withOnNext(_worker ?=> event => _worker.submit(event))
    .build()
  override private[pods] def subscribe(s: IStream[T]): Unit =
    worker.subscribe(s.worker.asInstanceOf) // not sure why this is needed
  override private[pods] def submit(event: T): Int = worker.submit(event)
  override def close(): Unit = worker.close()

private[pods] object OStream:
  def apply[T](): OStream[T] = new OChannelImpl {}
