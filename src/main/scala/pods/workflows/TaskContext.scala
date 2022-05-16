package pods.workflows

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.concurrent.Future

/** [[TaskContext]] */
private[pods] sealed trait TaskContext[I, O]:
  /** static input channel to the task */
  private[pods] val ic: IChannel[I]

  /** static output channel to the task */
  private[pods] val oc: OChannel[O]

  /** external input channel to this task, created dynamically on use */
  val self: IChannel[I]

  /** state of the task */
  def state: TaskState[Any, Any]

  /** emit an event, this is published to the [[oc]] */
  def emit(event: O): Unit

  /** send an event to the provided channel [[ic]] */
  def send[T](ic: IChannel[T], event: T): Unit

  /** request/response, send a request to [[ic]] and expect a response on a
    * freshly created channel
    */
  def ask[T, U](ic: IChannel[T], requestFactory: IChannel[U] => T): Future[U]

  /** await the completion of the provided future */
  def await[T](future: Future[T])(
      cnt: TaskContext[T, O] ?=> T => TaskBehavior[I, O]
  ): TaskBehavior[I, O]

  /** logger */
  def log: Logger

/** [[TaskContext]] Implementation */
private[pods] class TaskContextImpl[I, O] extends TaskContext[I, O]:
  private[pods] val ic: IChannel[I] = IChannel[I]()
  private[pods] val oc: OChannel[O] = OChannel[O]()
  val self: IChannel[I] = IChannel[I]()
  def emit(event: O): Unit =
    oc.submit(event)
  def send[T](ic: IChannel[T], event: T): Unit =
    val newoc = OChannel[T]()
    newoc.subscribe(ic.asInstanceOf)
    newoc.submit(event)
    newoc.close()
  def ask[T, U](ic: IChannel[T], requestFactory: IChannel[U] => T): Future[U] =
    ???

  def await[T](future: Future[T])(
      cnt: TaskContext[T, O] ?=> T => TaskBehavior[I, O]
  ): TaskBehavior[I, O] = ???

  def state: TaskState[Any, Any] = TaskState()
  def log: Logger = LoggerFactory.getLogger(this.getClass)

/** [[TaskContext]] Factory */
object TaskContext:
  def apply[I, O](): TaskContext[I, O] =
    new TaskContextImpl[I, O]
