package portals.runtime.executor

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import portals._
import portals.application.task._
import portals.application.task.MapTaskStateExtension._
import portals.runtime.state.RocksDBStateBackendImpl
import portals.runtime.WrappedEvents.*

/** Internal API. Executor for Tasks.
  *
  * Executor for executing tasks. The executor is responsible for setting up the
  * task context and output collector, and executing the task on a batch of
  * events.
  */
private[portals] class TaskExecutorImpl:
  import TaskExecutorImpl.*

  // setup
  private val ctx = TaskContextImpl[Any, Any, Any, Any]()
  val oc = OutputCollectorImpl[Any, Any, Any, Any]()
  ctx.outputCollector = oc
  private val state = RocksDBStateBackendImpl()
  private var task: GenericTask[Any, Any, Any, Any] = _
  private var _replierTask: ReplierTaskKind[Any, Any, Any, Any] = _

  /** Setup the task executor for a specific task.
    *
    * To be called for every time a different task is executed.
    *
    * @param path
    *   path of the task
    * @param _task
    *   task to be executed
    */
  def setup(path: String, wfpath: String, _task: GenericTask[Any, Any, Any, Any]): Unit =
    ctx.path = path
    ctx.wfpath = wfpath
    ctx.state.path = path
    ctx.state.asInstanceOf[TaskStateImpl].stateBackend = state
    task = _task
    _replierTask = _task match
      case t @ ReplierTask(_, _) => t
      case t @ AskerReplierTask(_, _) => t
      case t @ _ => null
  end setup

  /** Cleanup a batch of events.
    *
    * Removes duplicate atom markers, duplicate seals, etc. Intended to be used
    * for the output of a single task when processing multiple inputs.
    *
    * @param batch
    *   batch of events to be cleaned
    * @return
    *   cleaned batch of events
    */
  def clean_events(batch: List[WrappedEvent[Any]]): List[WrappedEvent[Any]] =
    val seald = batch.exists { case Seal => true; case _ => false }
    val atomd = batch.exists { case Atom => true; case _ => false }
    val errord = batch.find { case Error(t) => true; case _ => false }

    if errord.isDefined then
      batch
        .filter {
          _ match
            case Atom => false
            case Seal => false
            case Error(t) => false
            case _ => true
        }
        .appended(errord.get)
        .appended(Atom)
        .appended(Seal)
    else if seald && atomd then
      batch
        .filter {
          _ match
            case Atom => false
            case Seal => false
            case _ => true
        }
        .appended(Atom)
        .appended(Seal)
    else if atomd then
      batch
        .filter {
          _ match
            case Atom => false
            case Seal => false
            case _ => true
        }
        .appended(Atom)
    else if seald then
      batch
        .filter {
          _ match
            case Atom => false
            case Seal => false
            case _ => true
        }
        .appended(Seal)
    else batch
  end clean_events

  /** Run the task on a batch of events.
    *
    * Stops processing in case of error, side effects are collected in `oc`.
    *
    * @note
    *   Before running this method for a new task, the `setup` method needs to
    *   be called, and any unwanted side-effects should be cleared.
    *
    * @param batch
    *   batch of wrapped events to be processed
    */
  def run_batch(batch: List[WrappedEvent[Any]]): Unit =

    // prepare
    val iter = batch.iterator
    var errord: Option[Throwable] = None

    // execute event until end or until errror
    while (iter.hasNext && errord.isEmpty) {
      this.run_wrapped_event(iter.next()) match
        case Success(_) => ()
        case Failure(t) =>
          oc.submit(Error(t))
          errord = Some(t)
    }

    // if there was an error, end atom and seal batch
    if errord.isDefined then
      oc.submit(Atom)
      oc.submit(Seal)
  end run_batch

  /** Run the task on a single event. */
  def run_event(event: WrappedEvent[Any]): Seq[WrappedEvent[Any]] =
    this.run_wrapped_event(event)
    this.oc.removeAll()

  /** Prepare a task behavior at runtime.
    *
    * This executes the initialization and returns the initialized task. This
    * needs to be called internally to initialize the task behavior before
    * execution.
    *
    * @param task
    *   task to be prepared
    * @return
    *   prepared task
    */
  def prepareTask[T, U, Req, Rep](
      task: GenericTask[T, U, Req, Rep],
  ): GenericTask[T, U, Req, Rep] =
    TaskExecutorImpl.prepareTask(task, this.ctx.asInstanceOf)

  /** Execute a wrapped event on the task.
    *
    * This is used to run a wrapped event. Works for both regular events, as
    * well as Ask events and Reply events.
    *
    * Make sure that the context has been prepared correspondingly before
    * calling this method, for example via calling `setup`.
    *
    * @param event
    *   wrapped event to be executed
    * @return
    *   success if the event was executed successfully, failure otherwise
    */
  private def run_wrapped_event(event: WrappedEvent[Any]): Try[Unit] = event match
    case Event(key, e) =>
      ctx.key = key
      ctx.task = task
      ctx.state.key = key
      scala.util.Try {
        task.onNext(using ctx)(e)
      } match
        case Success(_) => Success(())
        case Failure(t) => Failure(t)
    case Atom =>
      ctx.task = task
      task.onAtomComplete(using ctx)
      oc.submit(Atom)
      Success(())
    case Seal =>
      ctx.task = task
      task.onComplete(using ctx)
      oc.submit(Seal)
      Success(())
    case Error(t) =>
      ctx.task = task
      task.onError(using ctx)(t)
      oc.submit(Error(t))
      Failure(t)
    case Ask(key, meta, e) =>
      // task
      ctx.key = key
      ctx.task = task
      ctx.state.key = key
      // ask
      ctx.id = meta.id
      ctx.asker = meta.askingTask
      ctx.portal = meta.portal
      ctx.portalAsker = meta.askingWF
      ctx.askerKey = meta.askingKey
      _replierTask match
        case ReplierTask(_, f2) =>
          f2(ctx)(e)
        case AskerReplierTask(_, f2) =>
          f2(ctx)(e)
      Success(())
    case Reply(key, meta, e) =>
      // task
      ctx.key = key
      ctx.task = null // this.task is already null, but we set it to null to be sure
      ctx.state.key = key
      // reply
      run_and_cleanup_reply(meta.id, e)(using ctx)
      Success(())
  end run_wrapped_event // def

object TaskExecutorImpl:
  /** Prepare a task behavior at runtime.
    *
    * This executes the initialization and returns the initialized task. This
    * needs to be called internally to initialize the task behavior before
    * execution.
    *
    * @param task
    *   task to be prepared
    * @param ctx
    *   task context
    * @return
    *   prepared task
    */
  def prepareTask[T, U, Req, Rep](
      task: GenericTask[T, U, Req, Rep],
      ctx: TaskContextImpl[T, U, Req, Rep]
  ): GenericTask[T, U, Req, Rep] =
    /** internal recursive method */
    def prepareTaskRec(
        task: GenericTask[T, U, Req, Rep],
        ctx: TaskContextImpl[T, U, Req, Rep]
    ): GenericTask[T, U, Req, Rep] =
      // FIXME: (low prio) this will fail if we have a init nested inside of a normal behavior
      task match
        case InitTask(initFactory) => prepareTaskRec(initFactory(ctx), ctx)
        case _ => task

    /** prepare the task, recursively performing initialization */
    prepareTaskRec(task, ctx)
  end prepareTask // def

  /** Run the continuation `id` with the reply `r`.
    *
    * This is used to run the continuation of a reply. The continuation is
    * stored in the task context and is run via this method when the reply is
    * received. This completes the future.
    *
    * @param id
    *   id of the continuation
    * @param r
    *   reply to be executed
    */
  def run_and_cleanup_reply[T, U, Req, Rep](id: Int, r: Rep)(using
      actx: TaskContextImpl[T, U, Req, Rep]
  ): Unit =

    // setup state
    lazy val _futures: AskerTaskContext[_, _, _, Rep] ?=> PerTaskState[Map[Int, Rep]] =
      PerTaskState[Map[Int, Rep]]("futures", Map.empty)
    lazy val _continuations: AskerTaskContext[T, U, Req, Rep] ?=> PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]] =
      PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]]("continuations", Map.empty)
    lazy val _continuations_meta =
      PerTaskState[Map[Int, ContinuationMeta]]("continuations_meta", Map.empty)

    // set future
    _futures.update(id, r)

    // run continuation
    (_continuations.get(id), _continuations_meta.get(id)) match
      case (Some(continuation), None) =>
        // run continuation
        continuation(using actx)
      case (Some(continuation), Some(continuation_meta)) =>
        // setup context
        actx.id = continuation_meta.id
        actx.asker = continuation_meta.asker
        actx.portal = continuation_meta.portal
        actx.portalAsker = continuation_meta.portalAsker
        actx.askerKey = continuation_meta.askerKey
        // run continuation
        continuation(using actx)
      case (None, Some(continuation_meta)) => ??? // should not happen
      case (None, None) => () // do nothing, no continuation was saved for this future

    // cleanup future
    _futures.remove(id)

    // cleanup continuation
    _continuations.remove(id)
    _continuations_meta.remove(id)
  end run_and_cleanup_reply
