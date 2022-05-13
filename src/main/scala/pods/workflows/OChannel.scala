package pods.workflows

private[pods] sealed trait OChannel[T]:
  private[pods] val worker: Worker[EventWithId[T], EventWithId[T]]
  private[pods] def subscribe(s: IChannel[EventWithId[T]]): Unit
  private[pods] def submit(event: T): Int
  def close(): Unit

private[pods] class OChannelImpl[T] extends OChannel[T]:
  import Workers.*
  val id = scala.util.Random().nextInt()
  private[pods] val worker = Workers[EventWithId[T], EventWithId[T]]()
    .withOnNext(_worker ?=> t => _worker.submit(EventWithId(id, t.event)))
    .build()
  override private[pods] def subscribe(s: IChannel[EventWithId[T]]): Unit =
    worker.subscribe(s.worker.asInstanceOf) // not sure why this is needed
  override private[pods] def submit(event: T): Int = worker.submit(EventWithId(id, event))
  override def close(): Unit = worker.close()

private[pods] object OChannel:
  def apply[T](): OChannel[T] = new OChannelImpl {}
