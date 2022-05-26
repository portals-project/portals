package pods.workflows

import org.slf4j.Logger
import scala.concurrent.Future

/** TaskContext */
private[pods] trait TaskContext[I, O]:
  /** main input stream to the task */
  private[pods] val mainiref: IStream[I]

  /** main output stream to the task */
  private[pods] val mainoref: OStream[O]

  /** external input stream to this task, created dynamically on use */
  val iref: IStream[I]

  /** external output stream to this task, created dynamically on use */
  val oref: OStream[I]

  /** state of the task */
  def state: TaskState[Any, Any]

  /** emit an event, this is published to the [[mainoref]] */
  def emit(event: O): Unit

  /** send an event to the provided stream [[ic]] */
  def send[T](ic: IStream[T], event: T): Unit

  /** request/response, send a request to [[ic]] and expect a response on a
    * freshly created stream
    */
  def ask[T, U](ic: IStream[T], requestFactory: IStream[U] => T): Future[U]

  /** await the completion of the provided future */
  def await[T](future: Future[T])(
      cnt: TaskContext[T, O] ?=> T => TaskBehavior[I, O]
  ): TaskBehavior[I, O]

  /** logger */
  def log: Logger

/** [[TaskContext]] Factory */
object TaskContext:
  def apply[I, O](): TaskContext[I, O] =
    new TaskContextImpl[I, O]
