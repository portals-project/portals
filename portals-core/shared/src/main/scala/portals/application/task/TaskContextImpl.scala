package portals.application.task

import scala.scalajs.js.annotation.JSExportAll

import portals.application.task.AskerTaskContext
import portals.application.task.Continuation
import portals.application.task.ContinuationMeta
import portals.application.task.GenericTask
import portals.application.task.MapTaskContext
import portals.application.task.MapTaskStateExtension
import portals.application.task.MapTaskStateExtension.*
import portals.application.task.OutputCollector
import portals.application.task.PerTaskState
import portals.application.task.ProcessorTaskContext
import portals.application.task.ReplierTaskContext
import portals.application.task.TaskContext
import portals.application.task.TaskState
import portals.application.AtomicPortalRefKind
import portals.runtime.WrappedEvents.*
import portals.system.PortalsSystem
import portals.util.Future
import portals.util.FutureImpl
import portals.util.Key
import portals.util.Logger

@JSExportAll
private[portals] class TaskContextImpl[T, U, Req, Rep]
    extends TaskContext[T, U, Req, Rep]
    with MapTaskContext[T, U]
    with ProcessorTaskContext[T, U]
    with AskerTaskContext[T, U, Req, Rep]
    with ReplierTaskContext[T, U, Req, Rep]:

  //////////////////////////////////////////////////////////////////////////////
  // ProcessorTaskContext
  //////////////////////////////////////////////////////////////////////////////
  override val state: TaskState[Any, Any] = TaskState()

  override def emit(event: U): Unit = outputCollector.submit(Event(key, event))

  private lazy val _log = Logger(path)

  override def log: Logger = _log

  /** should be var so that it can be swapped out during runtime */
  private[portals] var path: String = _
  private[portals] var wfpath: String = _
  private[portals] var key: Key = _
  private[portals] var system: PortalsSystem = _
  private[portals] var outputCollector: OutputCollector[T, U, Any, Any] = _
  private[portals] var task: GenericTask[T, U, Req, Rep] = _

  //////////////////////////////////////////////////////////////////////////////
  // AskerTaskContext
  //////////////////////////////////////////////////////////////////////////////
  private lazy val _continuations =
    PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]]("continuations", Map.empty)
  private lazy val _continuations_meta =
    PerTaskState[Map[Int, ContinuationMeta]]("continuations_meta", Map.empty)

  override def ask(portal: AtomicPortalRefKind[Req, Rep])(msg: Req): Future[Rep] =
    val future: Future[Rep] = Future()
    outputCollector.ask(portal.path, this.path, msg, this.key, future.id, this.wfpath)
    future

  override def await(future: Future[Rep])(f: AskerTaskContext[_, U, Req, Rep] ?=> Unit): Unit =
    // update continuation
    _continuations.update(future.asInstanceOf[FutureImpl[_]].id, f)(using this)

    // update continuation meta information
    if this.asker != null then
      _continuations_meta.update(
        future.asInstanceOf[FutureImpl[_]].id,
        ContinuationMeta(
          this.id,
          this.asker,
          this.portal,
          this.portalAsker,
          this.askerKey
        )
      )(using this)

  //////////////////////////////////////////////////////////////////////////////
  // ReplierTaskContext
  //////////////////////////////////////////////////////////////////////////////
  /** should be var so that it can be swapped out during runtime */
  private[portals] var id: Int = _
  private[portals] var asker: String = _
  private[portals] var portal: String = _
  private[portals] var portalAsker: String = _
  private[portals] var askerKey: Key = _

  override def reply(msg: Rep): Unit =
    outputCollector.reply(msg, this.portal, this.asker, this.askerKey, this.id, this.portalAsker)
end TaskContextImpl // class
