package portals.runtime.executor

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import portals.*
import portals.application.task.*
import portals.application.task.MapTaskStateExtension.*
import portals.runtime.state.*

private[portals] class TaskExecutorImpl:
  // setup
  val ctx = TaskContextImpl[Any, Any, Any, Any]()
  val oc = OutputCollectorImpl[Any, Any, Any, Any]()
  ctx.outputCollector = oc
  private val state = MapStateBackendImpl()
  private var task: GenericTask[Any, Any, Any, Any] = _
  private var _replierTask: ReplierTask[Any, Any, Any, Any] = _

  /** Setup the task executor for a specific task.
    *
    * To be called for every time a different task is executed.
    *
    * @param path
    *   path of the task
    * @param _task
    *   task to be executed
    */
  def setup(path: String, _task: GenericTask[Any, Any, Any, Any]): Unit =
    ctx.path = path
    ctx.outputCollector = oc
    ctx.state.path = path
    ctx.state.asInstanceOf[TaskStateImpl[Any, Any]].stateBackend = state
    task = _task
    _replierTask = _task match
      case t @ ReplierTask(_, _) => t
      case t @ _ => null

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

  /** run the task on a batch of events, stops processing in case of error, side
    * effects are collected in `oc`.
    */
  def run_batch(batch: List[WrappedEvent[Any]]): Unit =
    val iter = batch.iterator
    var errord: Option[Throwable] = None

    // execute event until end or errror
    while (iter.hasNext && errord.isEmpty) {
      this.run_wrapped_event(iter.next()) match
        case Success(_) => ()
        case Failure(t) =>
          oc.submit(Error(t))
          errord = Some(t)
          println("error: " + t)
    }

    // if there was an error, end atom and seal batch
    if errord.isDefined then
      oc.submit(Atom)
      oc.submit(Seal)

  def run_and_cleanup_reply[T, U, Req, Rep](id: Int, r: Rep)(using
      actx: AskerTaskContext[T, U, Req, Rep]
  ): Unit =
    lazy val _futures: AskerTaskContext[_, _, _, Rep] ?=> PerTaskState[Map[Int, Rep]] =
      PerTaskState[Map[Int, Rep]]("futures", Map.empty)
    lazy val _continuations: AskerTaskContext[T, U, Req, Rep] ?=> PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]] =
      PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]]("continuations", Map.empty)
    // set future
    _futures.update(id, r)
    // run continuation
    _continuations.get(id) match
      case Some(continuation) => continuation(using actx)
      case None => () // do nothing, no continuation was saved for this future
    // cleanup future
    _futures.remove(id)
    // cleanup continuation
    _continuations.remove(id)
  end run_and_cleanup_reply // def

  private def run_wrapped_event(event: WrappedEvent[Any]): Try[Unit] = event match
    case Event(key, e) =>
      ctx.key = key
      ctx.task = task
      ctx.state.key = key
      scala.util.Try {
        task.onNext(using ctx)(e)
      }
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
      Failure(t)
    case Ask(key, meta, e) =>
      _replierTask.f2(ctx)(e)
      Success(())
    case Reply(key, meta, e) =>
      run_and_cleanup_reply(meta.id, e)(using ctx)
      Success(())
  end run_wrapped_event // def

  def clear(): Unit =
    oc.clear()
    oc.clearAsks()
    oc.clearReps()
