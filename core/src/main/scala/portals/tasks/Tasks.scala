package portals

private sealed trait Tasks

/** Core Task Factories. */
object Tasks extends Tasks:
  //////////////////////////////////////////////////////////////////////////////
  // Task Factories
  //////////////////////////////////////////////////////////////////////////////

  /** Behavior factory for generic task, returning a new task behavior. */
  def task[T, U](onNext: TaskContext[T, U] ?=> T => Task[T, U]): Task[T, U] =
    new BaseTask[T, U] {}._copy(_onNext = onNext)

  /** Behavior factory for handling incoming event and context. */
  def processor[T, U](onNext: TaskContext[T, U] ?=> T => Unit): Task[T, U] =
    Processor[T, U](ctx => onNext(using ctx))

  /** Behavior factory for emitting the same values as ingested. */
  def identity[T]: Task[T, T] =
    Identity[T]()

  /** Behavior factory for initializing the task before any events.
    *
    * Note: this may be **re-executed** more than once, every time that the task is restarted (e.g. after a failure).
    */
  def init[T, U](initFactory: TaskContext[T, U] ?=> Task[T, U]): Task[T, U] =
    Init[T, U](ctx => initFactory(using ctx))

  /** Behavior factory for using the same behavior as previous behavior. */
  def same[T, S]: Task[T, S] =
    // same behavior is compatible with previous behavior
    Same.asInstanceOf[Task[T, S]]

  //////////////////////////////////////////////////////////////////////////////
  // Task Implementations
  //////////////////////////////////////////////////////////////////////////////

  private[portals] case class Processor[T, U](
      _onNext: TaskContext[T, U] => T => Unit
  ) extends BaseTask[T, U]:
    override def onNext(using ctx: TaskContext[T, U])(event: T): Task[T, U] =
      _onNext(ctx)(event)
      Tasks.same
  end Processor // case class

  private[portals] case class Identity[T]() extends BaseTask[T, T]:
    override def onNext(using ctx: TaskContext[T, T])(event: T): Task[T, T] =
      ctx.emit(event)
      Tasks.same
  end Identity // case class

  private[portals] case class Init[T, U](
      initFactory: TaskContext[T, U] => Task[T, U]
  ) extends Task[T, U]:
    // wrapped initialized task
    var _task: Option[Task[T, U]] = None

    // initialize the task, or get the already initialized task
    private def prepOrGet(using ctx: TaskContext[T, U]): Task[T, U] =
      _task match
        case Some(task) => task
        case None =>
          _task = Some(prepareTask(this, ctx))
          _task.get

    override def onNext(using ctx: TaskContext[T, U])(t: T): Task[T, U] =
      this.prepOrGet.onNext(t)
      Tasks.same

    override def onError(using ctx: TaskContext[T, U])(t: Throwable): Task[T, U] =
      this.prepOrGet.onError(t)
      Tasks.same

    override def onComplete(using ctx: TaskContext[T, U]): Task[T, U] =
      this.prepOrGet.onComplete
      Tasks.same

    override def onAtomComplete(using ctx: TaskContext[T, U]): Task[T, U] =
      this.prepOrGet.onAtomComplete
      Tasks.same
  end Init // case class

  // this is fine, the methods are ignored as we reuse the previous behavior
  private[portals] case object Same extends TaskUnimpl[Nothing, Nothing]

  //////////////////////////////////////////////////////////////////////////////
  // Base Tasks
  //////////////////////////////////////////////////////////////////////////////

  /** Unimplemented Task */
  private[portals] class TaskUnimpl[T, U] extends Task[T, U]:
    override def onNext(using ctx: TaskContext[T, U])(t: T): Task[T, U] = ???
    override def onError(using ctx: TaskContext[T, U])(t: Throwable): Task[T, U] = ???
    override def onComplete(using ctx: TaskContext[T, U]): Task[T, U] = ???
    override def onAtomComplete(using ctx: TaskContext[T, U]): Task[T, U] = ???

  /** Base Task */
  private[portals] class BaseTask[T, U] extends Task[T, U]:
    override def onNext(using ctx: TaskContext[T, U])(t: T): Task[T, U] = Tasks.same
    override def onError(using ctx: TaskContext[T, U])(t: Throwable): Task[T, U] = Tasks.same
    override def onComplete(using ctx: TaskContext[T, U]): Task[T, U] = Tasks.same
    override def onAtomComplete(using ctx: TaskContext[T, U]): Task[T, U] =
      // TODO: we should remove ctx.fuse() from here, but this currently breaks the tests
      ctx.fuse()
      Tasks.same

  //////////////////////////////////////////////////////////////////////////////
  // Checks and Preparations
  //////////////////////////////////////////////////////////////////////////////

  /** Prepare a task behavior at runtime.
    *
    * This executes the initialization and returns the initialized task. This needs to be called internally to
    * initialize the task behavior before execution.
    */
  private[portals] def prepareTask[T, U](task: Task[T, U], ctx: TaskContext[T, U]): Task[T, U] =
    /** internal recursive method */
    def prepareTaskRec(task: Task[T, U], ctx: TaskContext[T, U]): Task[T, U] =
      // FIXME: (low prio) this will fail if we have a init nested inside of a normal behavior
      task match
        case Init(initFactory) => prepareTaskRec(initFactory(ctx), ctx)
        case _ => task

    /** prepare the task, recursively performing initialization */
    task match
      case Same => throw new Exception("Same is not a valid initial task behavior")
      case _ => prepareTaskRec(task, ctx)
  end prepareTask // def

////////////////////////////////////////////////////////////////////////////////
// Task Extensions
////////////////////////////////////////////////////////////////////////////////
/** Task Extensions. */
object TaskExtensions:
  extension (t: Tasks) {

    /** behavior factory for map */
    def map[T, U](f: MapTaskContext[T, U] ?=> T => U): Task[T, U] =
      Tasks.Init[T, U] { ctx =>
        val _ctx = MapTaskContext.fromTaskContext(ctx)
        Tasks.Processor { ctx => event =>
          _ctx._ctx = ctx
          ctx.emit((f(using _ctx)(event)))
        }
      }

    /** behavior factory for flatMap */
    def flatMap[T, U](f: MapTaskContext[T, U] ?=> T => TraversableOnce[U]): Task[T, U] =
      Tasks.Init[T, U] { ctx =>
        val _ctx = MapTaskContext.fromTaskContext(ctx)
        Tasks.Processor { ctx => event =>
          _ctx._ctx = ctx
          f(using _ctx)(event).iterator.foreach(ctx.emit)
        }
      }

    /** behavior factory for filter */
    def filter[T](p: T => Boolean): Task[T, T] =
      Tasks.Processor[T, T] { ctx => event =>
        if (p(event)) ctx.emit(event)
      }

    /** behavior factory for modifying the key context */
    private[portals] def key[T](f: T => Int): Task[T, T] =
      Tasks.Processor[T, T] { ctx => x =>
        ctx.key = Key(f(x))
        ctx.emit(x)
      }
  }
end TaskExtensions
export TaskExtensions.*

//////////////////////////////////////////////////////////////////////////////
// Behavior Modifying Combinators
//////////////////////////////////////////////////////////////////////////////
/** Task Behavior Combinators. */
object TaskBehaviorCombinators:
  extension [T, U](task: Task[T, U]) {
    def withOnNext(f: TaskContext[T, U] ?=> T => Task[T, U]): Task[T, U] =
      task._copy(_onNext = f)

    def withOnError(f: TaskContext[T, U] ?=> Throwable => Task[T, U]): Task[T, U] =
      task._copy(_onError = f)

    def withOnComplete(f: TaskContext[T, U] ?=> Task[T, U]): Task[T, U] =
      task._copy(_onComplete = f)

    def withOnAtomComplete(f: TaskContext[T, U] ?=> Task[T, U]): Task[T, U] =
      task._copy(_onAtomComplete = f)
  }
end TaskBehaviorCombinators
export TaskBehaviorCombinators.*

////////////////////////////////////////////////////////////////////////////////
// VSM Extension
////////////////////////////////////////////////////////////////////////////////
/** VSM Extension. */
object VSMExtension:
  extension (t: Tasks) {

    /** Vsm behavior factory
      *
      * The inner behavior should return the next state, for this we recommend the use of `Tasks.task`, note that the
      * use of `Tasks.processor` will not work, as it returns `Unit` and not the next behavior. Do not use vsm for the
      * inner behavior, this will lead to an infinite loop and crash.
      *
      * Example:
      *
      * {{{
      * val init = Tasks.task { event => started }
      * val started = Tasks.task { event => init }
      * val vsm = Tasks.vsm[Int, Int] { init }
      * }}}
      */
    def vsm[T, U](defaultTask: Task[T, U]): Task[T, U] = Tasks.init[T, U] { ctx ?=>
      val _vsm_state = PerKeyState[Task[T, U]]("$_vsm_state", defaultTask)
      Tasks.processor[T, U] { ctx ?=> event =>
        _vsm_state.get().onNext(event) match
          case Tasks.Same => () // do nothing, keep same behavior
          case t @ _ => _vsm_state.set(t)
      }
    }
  }
end VSMExtension
export VSMExtension.*

////////////////////////////////////////////////////////////////////////////////
// Step Extension
////////////////////////////////////////////////////////////////////////////////
/** Step Extension. */
object StepExtension:

  private[portals] case class Stepper[T, U](steppers: List[Steppers[T, U]]) extends Task[T, U]:
    private val index: TaskContext[T, U] ?=> PerTaskState[Int] = PerTaskState("$index", 0)
    private val loopcount: TaskContext[T, U] ?=> PerTaskState[Int] = PerTaskState("$loopcount", 0)
    private val size: Int = steppers.size

    // init to first stepper
    private var _curr: Task[T, U] = steppers.head.task

    override def onNext(using ctx: TaskContext[T, U])(t: T): Task[T, U] =
      _curr.onNext(t)

    override def onError(using ctx: TaskContext[T, U])(t: Throwable): Task[T, U] =
      _curr.onError(t)

    override def onComplete(using ctx: TaskContext[T, U]): Task[T, U] =
      _curr.onComplete

    override def onAtomComplete(using ctx: TaskContext[T, U]): Task[T, U] =
      _curr.onAtomComplete

      // update `_curr` to next stepper
      steppers(index.get() % size) match
        case Step(_) =>
          index.set(index.get() + 1)
          _curr = steppers(index.get() % size).task

        case Loop(_, count) =>
          loopcount.set(loopcount.get() + 1)
          if loopcount.get() >= count then
            loopcount.set(0)
            index.set(index.get() + 1)
            _curr = steppers(index.get() % size).task

      Tasks.same // return same behavior :)
    end onAtomComplete
  end Stepper // case class

  private[portals] sealed trait Steppers[T, U](val task: Task[T, U])
  private[portals] case class Step[T, U](override val task: Task[T, U]) extends Steppers[T, U](task)
  private[portals] case class Loop[T, U](override val task: Task[T, U], count: Int) extends Steppers[T, U](task)

  extension [T, U](task: Task[T, U]) {

    /** Behavior factory for taking steps over atoms. This will execute the provided `_task` for the following atom. */
    def withStep(_task: Task[T, U]): Task[T, U] = task match
      case stepper: Stepper[T, U] => stepper.copy(steppers = stepper.steppers :+ Step(_task))
      case _ => Stepper(List(Step(task), Step(_task)))

    /** Behavior factory for looping behaviors over atoms. This will execute the provided `_task` for the following
      * `count` atoms.
      */
    def withLoop(count: Int)(_task: Task[T, U]): Task[T, U] = task match
      case stepper: Stepper[T, U] => stepper.copy(steppers = stepper.steppers :+ Loop(_task, count))
      case _ => Stepper(List(Step(task), Loop(_task, count)))
  }
end StepExtension
export StepExtension.*

////////////////////////////////////////////////////////////////////////////////
// WithAndThen Extension
////////////////////////////////////////////////////////////////////////////////
/** WithAndThen Extension. */
object WithAndThenExtension:
  private[portals] trait WithAndThenContext[T, U] extends TaskContext[T, U]:
    var emitted: Seq[U]
    def reset(): Unit

  def fromTaskContext[T, U](ctx: TaskContext[T, U]) =
    new WithAndThenContext[T, U] {
      var emitted: Seq[U] = Seq.empty[U]
      override def reset(): Unit = emitted = Seq.empty[U]
      override def state: TaskState[Any, Any] = _ctx.state
      override def emit(event: U) = emitted = emitted :+ event
      override def log: Logger = _ctx.log
      override private[portals] def fuse(): Unit = ???
      private[portals] var key: portals.Key[Int] = ctx.key
      private[portals] var path: String = ctx.path
      private[portals] var system: portals.SystemContext = ctx.system
      private[portals] var _ctx: TaskContext[T, U] = ctx
    }
  end fromTaskContext // def

  extension [T, U](task: Task[T, U]) {

    /** Chaining a task with another `_task`, the tasks will share state. */
    def withAndThen[TT](_task: Task[U, TT]): Task[T, TT] =
      Tasks.init[T, TT] { ctx ?=>
        val _ctx = fromTaskContext(ctx).asInstanceOf[WithAndThenContext[T, U]]
        Tasks.processor[T, TT] { event =>
          task.onNext(using _ctx.asInstanceOf)(event)
          _ctx.emitted.foreach { event => _task.onNext(using ctx.asInstanceOf)(event) }
          _ctx.reset()
        }
      }

    // Optionally, if there are issues with nested inits:
    // def withAndThen[TT](_task: Task[U, TT]): Task[T, TT] =
    //   Tasks.processor[T, TT] { ctx ?=> event =>
    //     val _ctx = fromTaskContext[T, U](ctx.asInstanceOf)
    //     task.onNext(using _ctx)(event)
    //     _ctx.emitted.foreach { event => _task.onNext(using ctx.asInstanceOf)(event) }
    //     _ctx.reset()
    //   }
  }
end WithAndThenExtension
export WithAndThenExtension.*

////////////////////////////////////////////////////////////////////////////////
// WithWrapper Extension
////////////////////////////////////////////////////////////////////////////////
/** WithWrapper Extension. */
object WithWrapperExtension:

  extension [T, U](task: Task[T, U]) {

    /** Wrapping around the behavior of a task. The wrapped behavior is accessible for use.
      *
      * Example use:
      * {{{
      * Tasks.map[Int, Int] { _ + 5 }
      *   .withWrapper{ ctx ?=> wrapped => event =>
      *     if event < 3 then ctx.emit(0) else wrapped(event)
      *   }
      * }}}
      */
    def withWrapper(f: TaskContext[T, U] ?=> (TaskContext[T, U] ?=> T => Task[T, U]) => T => Unit): Task[T, U] =
      Tasks.processor[T, U] { ctx ?=> event => f(task.onNext)(event) }

    // Optionally, wrapping around a full task.
    // /** f: ctx => wrapped => task */
    // def withWrapper(f: TaskContext[T, U] ?=> Task[T, U] => Task[T, U]): Task[T, U] =
    //   Tasks.init[T, U] { ctx ?=> f(using ctx)(task) }
  }
end WithWrapperExtension
export WithWrapperExtension.*

////////////////////////////////////////////////////////////////////////////////
// Portals Extension
////////////////////////////////////////////////////////////////////////////////
/** Portals Extension. */
object PortalsExtension:
  import Tasks.*

  /** internal API */
  private[portals] case class Ask[T](id: Int, event: T)

  /** internal API */
  private[portals] case class Reply[T](id: Int, event: T)

  class PortalsTasks[Req, Rep]():
    def asker[T, U](f: AskerTaskContext[T, U, Req, Rep] ?=> T => Unit): AskerTask[T, U, Req, Rep] =
      new AskerTask[T, U, Req, Rep](ctx => f(using ctx))

    def replier[T, U](f1: TaskContext[T, U] ?=> T => Unit)(
        f2: ReplierTaskContext[T, U, Req, Rep] ?=> Req => Unit
    ): ReplierTask[T, U, Req, Rep] =
      new ReplierTask[T, U, Req, Rep](ctx => f1(using ctx), ctx => f2(using ctx))

  extension (t: Tasks) {
    /* Note: the reason we have this extra step via `portal` is to avoid the user having to specify the Req and Rep types. */
    def portal[Req, Rep](portals: AtomicPortalRefType[Req, Rep]*) =
      new PortalsTasks[Req, Rep]()
  }

  type Continuation[T, U, Req, Rep] = AskerTaskContext[T, U, Req, Rep] ?=> Task[T, U]

  private[portals] case class AskerTask[T, U, Req, Rep](
      f: AskerTaskContext[T, U, Req, Rep] => T => Unit
  ) extends BaseTask[T, U]:

    override def onNext(using ctx: TaskContext[T, U])(t: T): Task[T, U] =
      f(ctx.asInstanceOf)(t)
      Tasks.same

  private[portals] case class ReplierTask[T, U, Req, Rep](
      f1: TaskContext[T, U] => T => Unit,
      f2: ReplierTaskContext[T, U, Req, Rep] => Req => Unit
  ) extends BaseTask[T, U]:

    def requestingOnNext(using ctx: ReplierTaskContext[T, U, Req, Rep])(req: Req): Unit =
      f2(ctx)(req)

    override def onNext(using ctx: TaskContext[T, U])(t: T): Task[T, U] =
      f1(ctx)(t)
      Tasks.same

end PortalsExtension
export PortalsExtension.*
