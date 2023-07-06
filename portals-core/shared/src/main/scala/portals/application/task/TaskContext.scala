package portals.application.task

import portals.application.*
import portals.application.task.TaskState
import portals.util.Future
import portals.util.Key
import portals.util.Logger

private[portals] sealed trait GenericGenericTaskContext

private[portals] sealed trait GenericTaskContext[T, U, Req, Rep] extends GenericGenericTaskContext

private[portals] trait EmittingTaskContext[U] extends GenericTaskContext[_, U, _, _]:
  /** Emit an event */
  def emit(u: U): Unit

private[portals] trait StatefulTaskContext extends GenericTaskContext[_, _, _, _]:
  /** State of the task, scoped by the contextual invocation context */
  def state: TaskState[Any, Any]

private[portals] trait LoggingTaskContext extends GenericTaskContext[_, _, _, _]:
  /** Logger, used to log messages. */
  def log: Logger

private[portals] trait AskingTaskContext[Req, Rep] extends GenericTaskContext[_, _, Req, Rep]:
  /** Ask the `portal` with `msg`, returns a future of the reply. */
  def ask(portal: AtomicPortalRefKind[Req, Rep])(msg: Req): Future[Rep]

private[portals] trait AwaitingTaskContext[U, Req, Rep] extends GenericTaskContext[_, U, Req, Rep]:
  /** Await for the completion of the `future`. */
  def await(future: Future[Rep])(f: AskerTaskContext[_, U, Req, Rep] ?=> Unit): Unit

private[portals] trait ReplyingTaskContext[Rep] extends GenericTaskContext[_, _, _, Rep]:
  /** Reply with msg to the handled request. */
  def reply(msg: Rep): Unit

private[portals] trait KeyTaskContext extends GenericTaskContext[_, _, _, _]:
  /** Internal API. Access and modify the key of a task. WARNING: can break the
    * system.
    */
  private[portals] var key: Key

private[portals] trait ProcessorTaskContext[T, U]
    extends GenericTaskContext[T, U, _, _]
    with EmittingTaskContext[U]
    with StatefulTaskContext
    with LoggingTaskContext

private[portals] trait MapTaskContext[T, U]
    extends GenericTaskContext[T, U, _, _]
    with StatefulTaskContext
    with LoggingTaskContext

private[portals] trait AskerTaskContext[T, U, Req, Rep]
    extends GenericTaskContext[T, U, Req, Rep]
    with EmittingTaskContext[U]
    with StatefulTaskContext
    with LoggingTaskContext
    with AskingTaskContext[Req, Rep]
    with AwaitingTaskContext[U, Req, Rep]

private[portals] trait ReplierTaskContext[T, U, Req, Rep]
    extends GenericTaskContext[T, U, Req, Rep]
    with EmittingTaskContext[U]
    with StatefulTaskContext
    with LoggingTaskContext
    with ReplyingTaskContext[Rep]

private[portals] trait AskerReplierTaskContext[T, U, Req, Rep]
    extends GenericTaskContext[T, U, Req, Rep]
    with EmittingTaskContext[U]
    with StatefulTaskContext
    with LoggingTaskContext
    with AskingTaskContext[Req, Rep]
    with AwaitingTaskContext[U, Req, Rep]
    with ReplyingTaskContext[Rep]
    with AskerTaskContext[T, U, Req, Rep]
    with ReplierTaskContext[T, U, Req, Rep]

private[portals] trait TaskContext[T, U, Req, Rep]
    extends GenericTaskContext[T, U, Req, Rep]
    with ProcessorTaskContext[T, U]
    with MapTaskContext[T, U]
    with AskerTaskContext[T, U, Req, Rep]
    with ReplierTaskContext[T, U, Req, Rep]
    with AskerReplierTaskContext[T, U, Req, Rep]
