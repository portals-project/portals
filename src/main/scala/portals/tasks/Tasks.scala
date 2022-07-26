package portals

import scala.annotation.targetName

trait Tasks

object Tasks extends Tasks:
  //////////////////////////////////////////////////////////////////////////////
  // Task Factories
  //////////////////////////////////////////////////////////////////////////////

  // VSM disabled for now until we have implemented it properly.
  // /** behavior factory for handling incoming event and context with a virtual state machine */
  // def vsm[I, O](
  //     onNext: TaskContext[I, O] ?=> I => Task[I, O]
  // ): Task[I, O] =
  //   VSM[I, O](ctx => onNext(using ctx))

  /** behavior factory for handling incoming event and context */
  def processor[I, O](
      onNext: TaskContext[I, O] ?=> I => Unit
  ): Task[I, O] =
    Process[I, O](tctx => onNext(using tctx))

  /** behavior factory for map */
  def map[I, O](f: MapTaskContext[I, O] ?=> I => O): Task[I, O] =
    // todo: reduce context initialization to once per task with init behavior
    Process[I, O](tctx =>
      x =>
        val ctx = MapTaskContext.fromTaskContext(tctx)
        tctx.emit(
          f(using ctx)(x)
        )
    )

  /** behavior factory for flatMap */
  def flatMap[I, O](f: MapTaskContext[I, O] ?=> I => TraversableOnce[O]): Task[I, O] =
    // todo: reduce context initialization to once per task with init behavior
    Process[I, O](tctx =>
      x =>
        val ctx = MapTaskContext.fromTaskContext(tctx)
        f(using ctx)(x).foreach(tctx.emit(_))
    )

  def filter[T](p: T => Boolean): Task[T, T] =
    flatMap(x => if (p(x)) List(x) else Nil)

  /** behavior factory for emitting the same values as ingested */
  def identity[T]: Task[T, T] =
    Identity[T]()

  /** behavior factory for modifying the key context */
  private[portals] def key[T](f: T => Int): Task[T, T] =
    Process[T, T] { tctx => x =>
      tctx.key = Key(f(x))
      tctx.emit(x)
    }

  /** Behavior factory for initializing the task before any events. Note: this may be **re-executed** more than once,
    * every time that the task is restarted (e.g. after a failure).
    */
  def init[T, U](initFactory: TaskContext[T, U] ?=> Task[T, U]): Task[T, U] =
    Init[T, U](tctx => initFactory(using tctx))

  /** behavior factory for using the same behavior as previous behavior */
  def same[T, S]: Task[T, S] =
    // same behavior is compatible with previous behavior
    Same.asInstanceOf[Task[T, S]]

  // TODO: currently the portals need to have matching Req and Rep type, this should be fixed and instead we should
  // allow differing types for Req and Rep for the portals.
  def portals[Req, Rep, Portals <: (Singleton & AtomicPortalRefType[Req, Rep])](portals: Portals*) =
    new PortalsTasks[Req, Rep, Portals]()

  class PortalsTasks[Req, Rep, Portals <: (Singleton & AtomicPortalRefType[Req, Rep])]():
    def asker[T, U](f: AskerTaskContext[T, U, Req, Rep, Portals] ?=> T => Task[T, U]): Task[T, U] =
      same // TODO: implement

    def replier[T, U](f1: TaskContext[T, U] ?=> T => Task[T, U])(
        f2: ReplierTaskContext[T, U, Req, Rep, Portals] ?=> Req => Task[T, U]
    ): Task[T, U] =
      same // TODO: implement

  //////////////////////////////////////////////////////////////////////////////
  // Task Implementations
  //////////////////////////////////////////////////////////////////////////////

  // private[portals] case class VSM[I, O](
  //     _onNext: TaskContext[I, O] => I => Task[I, O]
  // ) extends BaseTask[I, O]:
  //   override def onNext(tctx: TaskContext[I, O])(t: I): Task[I, O] =
  //     _onNext(tctx)(t)

  private[portals] case class Process[I, O](
      _onNext: TaskContext[I, O] => I => Unit
  ) extends BaseTask[I, O]:
    override def onNext(tctx: TaskContext[I, O])(event: I): Task[I, O] =
      _onNext(tctx)(event)
      Tasks.same

  private[portals] case class Identity[T]() extends BaseTask[T, T]:
    override def onNext(tctx: TaskContext[T, T])(event: T): Task[T, T] =
      tctx.emit(event)
      Tasks.same

  private[portals] case class Init[T, U](
      initFactory: TaskContext[T, U] => Task[T, U]
  ) extends TaskUnimpl[T, U]:
    // this is fine, the methods are ignored as it is unfolded during initialization

    // TODO: this should be initialized before execution instead by the **runtime**
    // when instantiating the task, and not handled by the behavior, but this works
    // for now :).
    var _task: Option[Task[T, U]] = None

    override def onNext(ctx: TaskContext[T, U])(t: T): Task[T, U] =
      _task match
        case Some(task) => task.onNext(ctx)(t)
        case None =>
          _task = Some(prepareTask(this, ctx))
          _task.get.onNext(ctx)(t)
      Tasks.same
    override def onError(ctx: TaskContext[T, U])(t: Throwable): Task[T, U] =
      _task match
        case Some(task) => task.onError(ctx)(t)
        case None =>
          _task = Some(prepareTask(this, ctx))
          _task.get.onError(ctx)(t)
      Tasks.same
    override def onComplete(ctx: TaskContext[T, U]): Task[T, U] =
      _task match
        case Some(task) => task.onComplete(ctx)
        case None =>
          _task = Some(prepareTask(this, ctx))
          _task.get.onComplete(ctx)
      Tasks.same
    override def onAtomComplete(ctx: TaskContext[T, U]): Task[T, U] =
      _task match
        case Some(task) => task.onAtomComplete(ctx)
        case None =>
          _task = Some(prepareTask(this, ctx))
          _task.get.onAtomComplete(ctx)
      Tasks.same

  private[portals] case object Same extends TaskUnimpl[Nothing, Nothing]
  // this is fine, the methods are ignored as we reuse the previous behavior

  //////////////////////////////////////////////////////////////////////////////
  // Base Tasks
  //////////////////////////////////////////////////////////////////////////////

  /** Unimplemented Task */
  private[portals] class TaskUnimpl[I, O] extends Task[I, O]:
    override def onNext(ctx: TaskContext[I, O])(t: I): Task[I, O] = ???

    override def onError(ctx: TaskContext[I, O])(t: Throwable): Task[I, O] = ???

    override def onComplete(ctx: TaskContext[I, O]): Task[I, O] = ???

    override def onAtomComplete(ctx: TaskContext[I, O]): Task[I, O] = ???

  /** Base Task */
  private[portals] class BaseTask[I, O] extends Task[I, O]:
    override def onNext(ctx: TaskContext[I, O])(t: I): Task[I, O] = ???

    // TODO: implement onError and onComplete to have base behavior
    override def onError(ctx: TaskContext[I, O])(t: Throwable): Task[I, O] = ???

    override def onComplete(ctx: TaskContext[I, O]): Task[I, O] = ???

    override def onAtomComplete(ctx: TaskContext[I, O]): Task[I, O] =
      ctx.fuse()
      Tasks.same

  // needs to be called (internally) to setup the task for Init behaviors, called every time we start a new instance of a task
  private[portals] def prepareTask[T, U](task: Task[T, U], ctx: TaskContext[T, U]): Task[T, U] =
    // internal recursive method
    def prepareTaskRec(task: Task[T, U], ctx: TaskContext[T, U]): Task[T, U] =
      // FIXME: (low prio) this will fail if we have a init nested inside of a normal behavior
      task match
        case Init(initFactory) => prepareTaskRec(initFactory(ctx), ctx)
        case _ => task
    task match
      case Same => throw new Exception("Same is not a valid initial task behavior")
      case _ => prepareTaskRec(task, ctx)

object TaskExtensions:
  //////////////////////////////////////////////////////////////////////////////
  // Extension methods
  //////////////////////////////////////////////////////////////////////////////

  extension [T, U](task: Task[T, U]) {
    def withOnNext(f: TaskContext[T, U] ?=> T => Task[T, U]): Task[T, U] =
      task._copy(_onNext = ctx => f(using ctx))

    def withOnError(f: TaskContext[T, U] ?=> Throwable => Task[T, U]): Task[T, U] =
      task._copy(_onError = ctx => f(using ctx))

    def withOnComplete(f: TaskContext[T, U] ?=> Task[T, U]): Task[T, U] =
      task._copy(_onComplete = ctx => f(using ctx))

    def withOnAtomComplete(f: TaskContext[T, U] ?=> Task[T, U]): Task[T, U] =
      task._copy(_onAtomComplete = ctx => f(using ctx))
  }

////////////////////////////////////////////////////////////////////////////////
// Other Extensions
////////////////////////////////////////////////////////////////////////////////
export VSMExtension.*
object VSMExtension:
  extension (t: Tasks) {

    /** behavior factory for handling incoming event and context with a virtual state machine */
    def vsminit[T, U](defaultTask: Task[T, U]): Task[T, U] = Tasks.init[T, U] { ctx ?=>
      val _vsm_state = PerKeyState[Task[T, U]]("$_vsm_state", defaultTask)
      Tasks.processor[T, U] { ctx ?=> event =>
        _vsm_state.get().onNext(ctx)(event) match
          case Tasks.Same => () // do nothing, keep same behavior
          case t @ _ => _vsm_state.set(t)
      }
    }

    /** behavior factory for handling incoming event and context with a virtual state machine */
    def vsm[T, U](_onNext: TaskContext[T, U] ?=> T => Task[T, U]): Task[T, U] = Tasks.processor[T, U] { ctx ?=> event =>
      _onNext(using ctx)(event) match
        case Tasks.Same => () // do nothing, keep same behavior
        case t @ _ =>
          val _vsm_state = PerKeyState[Task[T, U]]("$_vsm_state", null)
          _vsm_state.set(t)
    }
  }

export StepExtension.*
object StepExtension:

  private[portals] case class Stepper[T, U](steppers: List[Steppers[T, U]]) extends Task[T, U]:
    val index: TaskContext[T, U] ?=> PerTaskState[Int] = PerTaskState("$index", 0)
    val loopcount: TaskContext[T, U] ?=> PerTaskState[Int] = PerTaskState("$loopcount", 0)
    val size = steppers.size

    override def onNext(ctx: TaskContext[T, U])(t: T): Task[T, U] =
      steppers(index(using ctx).get() % size).task.onNext(ctx)(t)

    override def onError(ctx: TaskContext[T, U])(t: Throwable): Task[T, U] =
      steppers(index(using ctx).get() % size).task.onError(ctx)(t)

    override def onComplete(ctx: TaskContext[T, U]): Task[T, U] =
      steppers(index(using ctx).get() % size).task.onComplete(ctx)

    override def onAtomComplete(ctx: TaskContext[T, U]): Task[T, U] =
      steppers(index(using ctx).get() % size) match
        case Step(_) =>
          steppers(index(using ctx).get() % size).task.onAtomComplete(ctx)
          index(using ctx).set(index(using ctx).get() + 1)

        case Loop(_, count) =>
          loopcount(using ctx).set(loopcount(using ctx).get() + 1)
          if loopcount(using ctx).get() >= count then
            steppers(index(using ctx).get() % size).task.onAtomComplete(ctx)
            loopcount(using ctx).set(0)
            index(using ctx).set(index(using ctx).get() + 1)
          else steppers(index(using ctx).get() % size).task.onAtomComplete(ctx)
      ctx.fuse() // TODO: we should remove fuse from the user-space :).
      Tasks.same

  private[portals] sealed trait Steppers[T, U](val task: Task[T, U])
  private[portals] case class Step[T, U](override val task: Task[T, U]) extends Steppers[T, U](task)
  private[portals] case class Loop[T, U](override val task: Task[T, U], count: Int) extends Steppers[T, U](task)

  extension [T, U](task: Task[T, U]) {
    def withStep(_task: Task[T, U]): Task[T, U] = task match
      case stepper: Stepper[T, U] => stepper.copy(steppers = stepper.steppers :+ Step(_task))
      case _ => Stepper(List(Step(task), Step(_task)))

    def withLoop(count: Int)(_task: Task[T, U]): Task[T, U] = task match
      case stepper: Stepper[T, U] => stepper.copy(steppers = stepper.steppers :+ Loop(_task, count))
      case _ => Stepper(List(Step(task), Loop(_task, count)))
  }

export WithAndThenExtension.*
object WithAndThenExtension:
  private[portals] trait WithAndThenContext[T, U] extends TaskContext[T, U]:
    var emitted: Seq[U]

  def withAndThenContext[T, U](ctx: TaskContext[T, U]) =
    new WithAndThenContext[T, U] {
      var emitted = Seq.empty[U]
      override def state: TaskState[Any, Any] = ctx.state
      override def emit(event: U) = emitted = emitted :+ event
      override private[portals] def fuse(): Unit = ctx.fuse()
      override def log: Logger = ctx.log
      private[portals] var path: String = ctx.path
      private[portals] var key: Key[Int] = ctx.key
      private[portals] var system: SystemContext = ctx.system
    }

  extension [T, U](task: Task[T, U]) {
    def withAndThen[TT](_task: Task[U, TT]): Task[T, TT] =
      Tasks.processor[T, TT] { ctx ?=> event =>
        val _ctx = withAndThenContext[T, U](ctx.asInstanceOf)
        task.onNext(_ctx)(event)
        _ctx.emitted.foreach { event => _task.onNext(ctx.asInstanceOf)(event) }
      }
  }

export WithWrapperExtension.*
object WithWrapperExtension:

  extension [T, U](task: Task[T, U]) {

    // Alternative
    // /** f: ctx => wrappd => task */
    // def withWrapper(f: TaskContext[T, U] ?=> Task[T, U] => Task[T, U]): Task[T, U] =
    //   Tasks.init[T, U] { ctx ?=> f(using ctx)(task) }

    /** f: ctx => wrappd => event => Unit */
    @targetName("withWrapper2")
    def withWrapper(f: TaskContext[T, U] ?=> (TaskContext[T, U] ?=> T => Task[T, U]) => T => Unit): Task[T, U] =
      Tasks.processor[T, U] { ctx ?=> event => f(using ctx)(task.onNext(ctx))(event) }
  }
