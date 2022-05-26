package pods.workflows

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.concurrent.Future

/** [[TaskContext]] Implementation */
private[pods] class TaskContextImpl[I, O] extends TaskContext[I, O]:
  private[pods] val mainiref: IStream[I] = IStream[I]()
  private[pods] val mainoref: OStream[O] = OStream[O]()
  val iref: IStream[I] = IStream[I]()
  val oref: OStream[I] = OStream[I]()
  def emit(event: O): Unit =
    mainoref.submit(event)
  def send[T](ic: IStream[T], event: T): Unit =
    val neworef = OStream[T]()
    neworef.subscribe(ic.asInstanceOf)
    neworef.submit(event)
    neworef.close()
  def ask[T, U](ic: IStream[T], requestFactory: IStream[U] => T): Future[U] =
    ???

  def await[T](future: Future[T])(
      cnt: TaskContext[T, O] ?=> T => TaskBehavior[I, O]
  ): TaskBehavior[I, O] = ???

  def state: TaskState[Any, Any] = TaskState()
  def log: Logger = LoggerFactory.getLogger(this.getClass)