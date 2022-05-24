package pods.workflows

private[pods] sealed trait IStream[T]:
  private[pods] val worker: Worker[T, T]
  def close(): Unit

private[pods] class IChannelImpl[T] extends IStream[T]:
  import Workers.*
  private[pods] val worker = Workers[T, T]()
    .withOnNext(_worker ?=> event => _worker.submit(event))
    .build()
  override def close(): Unit = worker.close()

private[pods] object IStream:
  def apply[T](): IStream[T] = new IChannelImpl[T] {}
