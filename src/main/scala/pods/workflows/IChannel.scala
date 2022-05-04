package pods.workflows

sealed trait IChannel[T]:
  private[pods] val worker: Worker[T, T]
  def close(): Unit

private[pods] class IChannelImpl[T] extends IChannel[T]:
  import Workers.*
  private[pods] val worker = Workers[T, T]()
    .withOnNext(_worker ?=> t => _worker.submit(t))
    .build()
  override def close(): Unit = worker.close()

object IChannel:
  def apply[T](): IChannel[T] = new IChannelImpl {}
