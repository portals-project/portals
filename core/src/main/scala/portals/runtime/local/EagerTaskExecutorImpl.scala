package portals.runtime.local

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.typed.ActorRef

import portals._
import portals.application.task._
import portals.application.task.MapTaskStateExtension._
import portals.runtime.executor.TaskExecutorImpl
import portals.runtime.state.MapStateBackendImpl
import portals.runtime.state.RocksDBStateBackendImpl
import portals.runtime.WrappedEvents.*
import portals.util.StateBackendFactory

/** Internal API. Executor for Tasks.
  *
  * Executor for executing tasks. The executor is responsible for setting up the
  * task context and output collector, and executing the task on a batch of
  * events.
  */
private[portals] class EagerTaskExecutorImpl:
  // import TaskExecutorImpl.*

  // setup
  private val ctx = TaskContextImpl[Any, Any, Any, Any]()
  private val oc = EagerOutputCollector()
  ctx.outputCollector = oc
  private val state = StateBackendFactory.getStateBackend()
  private var task: GenericTask[Any, Any, Any, Any] = _

  /** Setup the task executor for a specific task. */
  def setup(
      path: String,
      wfpath: String,
      _task: GenericTask[Any, Any, Any, Any],
      subscribers: Set[ActorRef[portals.runtime.local.AkkaRunnerBehaviors.Events.Event[_]]]
  ): Unit =
    ctx.path = path
    ctx.wfpath = wfpath
    ctx.state.path = path
    ctx.state.asInstanceOf[TaskStateImpl].stateBackend = state
    oc.setup(path, subscribers)
    task = _task
  end setup

  /** Run the task on a single event. */
  def run_event(event: WrappedEvent[Any]): Unit =
    this.run_wrapped_event(event)

  /** Prepare a task behavior at runtime. */
  def prepareTask[T, U, Req, Rep](
      task: GenericTask[T, U, Req, Rep],
  ): GenericTask[T, U, Req, Rep] =
    TaskExecutorImpl.prepareTask(task, this.ctx.asInstanceOf)

  /** Execute a wrapped event on the task. */
  private def run_wrapped_event(event: WrappedEvent[Any]): Unit = event match
    case Event(key, e) =>
      ctx.key = key
      ctx.state.key = key
      task.onNext(using ctx)(e)
    case Atom =>
      task.onAtomComplete(using ctx)
      oc.submit(Atom)
    case Seal =>
      task.onComplete(using ctx)
      oc.submit(Seal)
    case Error(t) =>
      task.onError(using ctx)(t)
      oc.submit(Error(t))
    case _ => ???
  end run_wrapped_event // def
