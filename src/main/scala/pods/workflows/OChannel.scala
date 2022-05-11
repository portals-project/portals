package pods.workflows

private[pods] sealed trait OChannel[T]:
  private[pods] val worker: Worker[T, T]
  private[pods] def subscribe(s: IChannel[_ >: T]): Unit
  private[pods] def submit(event: T): Int
  def close(): Unit

private[pods] class OChannelImpl[T] extends OChannel[T]:
  import Workers.*
  private[pods] val worker = Workers[T, T]()
    .withOnNext(_worker ?=> t => _worker.submit(t))
    .build()
  override private[pods] def subscribe(s: IChannel[_ >: T]): Unit =
    worker.subscribe(s.worker)
  override private[pods] def submit(event: T): Int = worker.submit(event)
  override def close(): Unit = worker.close()

private[pods] object OChannel:
  def apply[T](): OChannel[T] = new OChannelImpl {}
