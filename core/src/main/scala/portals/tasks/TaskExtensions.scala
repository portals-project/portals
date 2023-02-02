package portals

////////////////////////////////////////////////////////////////////////////////
// Task Extensions
////////////////////////////////////////////////////////////////////////////////
/** Task Extensions. */
object TaskExtensions:
  extension (t: Tasks) {

    /** behavior factory for flatMap */
    def flatMap[T, U](f: MapTaskContext[T, U] ?=> T => TraversableOnce[U]): GenericTask[T, U, Nothing, Nothing] =
      ProcessorTask[T, U] { ctx => x => f(using ctx)(x).iterator.foreach(ctx.emit) }

    /** behavior factory for filter */
    def filter[T](p: T => Boolean): GenericTask[T, T, Nothing, Nothing] =
      ProcessorTask[T, T] { ctx => event =>
        if (p(event)) ctx.emit(event)
      }
  }
end TaskExtensions
export TaskExtensions.*

//////////////////////////////////////////////////////////////////////////////
// Behavior Modifying Combinators
//////////////////////////////////////////////////////////////////////////////
/** Task Behavior Combinators. */
object TaskBehaviorCombinators:
  extension [T, U, Req, Rep](task: GenericTask[T, U, Req, Rep]) {
    def withOnNext(f: ProcessorTaskContext[T, U] ?=> T => Unit): GenericTask[T, U, Req, Rep] =
      task._copy(_onNext = f)

    def withOnError(f: ProcessorTaskContext[T, U] ?=> Throwable => Unit): GenericTask[T, U, Req, Rep] =
      task._copy(_onError = f)

    def withOnComplete(f: ProcessorTaskContext[T, U] ?=> Unit): GenericTask[T, U, Req, Rep] =
      task._copy(_onComplete = f)

    def withOnAtomComplete(f: ProcessorTaskContext[T, U] ?=> Unit): GenericTask[T, U, Req, Rep] =
      task._copy(_onAtomComplete = f)
  }
end TaskBehaviorCombinators
export TaskBehaviorCombinators.*

////////////////////////////////////////////////////////////////////////////////
// VSM Extension
////////////////////////////////////////////////////////////////////////////////
/** VSM Extension. */
object VSMExtension:
  private[portals] trait VSMTask[T, U]:
    def onNext(using ctx: ProcessorTaskContext[T, U])(t: T): VSMTask[T, U]
    def onError(using ctx: ProcessorTaskContext[T, U])(t: Throwable): VSMTask[T, U]
    def onComplete(using ctx: ProcessorTaskContext[T, U]): VSMTask[T, U]
    def onAtomComplete(using ctx: ProcessorTaskContext[T, U]): VSMTask[T, U]

  object VSMTasks:
    /** Behavior factory for using the same behavior as previous behavior.
      */
    def same[T, S]: VSMTask[T, S] = Same.asInstanceOf[VSMTask[T, S]]

    def processor[T, U](f: ProcessorTaskContext[T, U] ?=> T => VSMTask[T, U]): VSMTask[T, U] =
      VSMProcessor(ctx => f(using ctx))

    private[portals] class BaseVSMTask[T, U]() extends VSMTask[T, U]:
      override def onNext(using ctx: ProcessorTaskContext[T, U])(t: T): VSMTask[T, U] = VSMTasks.same
      override def onError(using ctx: ProcessorTaskContext[T, U])(t: Throwable): VSMTask[T, U] = VSMTasks.same
      override def onComplete(using ctx: ProcessorTaskContext[T, U]): VSMTask[T, U] = VSMTasks.same
      override def onAtomComplete(using ctx: ProcessorTaskContext[T, U]): VSMTask[T, U] = VSMTasks.same

    private[portals] class VSMTaskUnimpl[T, U]() extends VSMTask[T, U]:
      override def onNext(using ctx: ProcessorTaskContext[T, U])(t: T): VSMTask[T, U] = ???
      override def onError(using ctx: ProcessorTaskContext[T, U])(t: Throwable): VSMTask[T, U] = ???
      override def onComplete(using ctx: ProcessorTaskContext[T, U]): VSMTask[T, U] = ???
      override def onAtomComplete(using ctx: ProcessorTaskContext[T, U]): VSMTask[T, U] = ???

    private[portals] case class VSMProcessor[T, U](_onNext: ProcessorTaskContext[T, U] => T => VSMTask[T, U])
        extends BaseVSMTask[T, U]:
      override def onNext(using ctx: ProcessorTaskContext[T, U])(event: T): VSMTask[T, U] = _onNext(ctx)(event)
    end VSMProcessor // case class

    // this is fine, the methods are ignored as we reuse the previous behavior
    private[portals] case object Same extends VSMTaskUnimpl[Nothing, Nothing]

  extension (t: Tasks) {

    /** Vsm behavior factory
      *
      * The inner behavior should return the next state, for this we recommend the use of [VSMExtension.processor], note
      * that the use of `Tasks.processor` will not work, as it returns `Unit` and not the next behavior. Warning: do not
      * use vsm for the inner behavior, this will lead to an infinite loop and crash.
      *
      * Example:
      *
      * {{{
      * val init = VSMExtension.processor { event => started }
      * val started = VSMExtension.processor { event => init }
      * val vsm = VSMExtension.vsm[Int, Int] { init }
      * }}}
      */
    def vsm[T, U](defaultTask: VSMTask[T, U]): GenericTask[T, U, Nothing, Nothing] = Tasks.init {
      lazy val _vsm_state = PerKeyState[VSMTask[T, U]]("$_vsm_state", defaultTask)
      Tasks.processor[T, U] { event =>
        _vsm_state.get().onNext(event) match
          case VSMTasks.Same => () // do nothing, keep same behavior
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

  private[portals] case class Stepper[T, U](steppers: List[Steppers[T, U]]) extends ExtensibleTask[T, U]:
    private val index: TaskContextImpl[T, U, Nothing, Nothing] ?=> PerTaskState[Int] = PerTaskState("$index", 0)
    private val loopcount: TaskContextImpl[T, U, Nothing, Nothing] ?=> PerTaskState[Int] = PerTaskState("$loopcount", 0)
    private val size: Int = steppers.size

    // init to first stepper
    private var _curr: GenericTask[T, U, Nothing, Nothing] = steppers.head.task

    override def onNext(using ctx: TaskContextImpl[T, U, Nothing, Nothing])(t: T): Unit =
      _curr.onNext(t)

    override def onError(using ctx: TaskContextImpl[T, U, Nothing, Nothing])(t: Throwable): Unit =
      _curr.onError(t)

    override def onComplete(using ctx: TaskContextImpl[T, U, Nothing, Nothing]): Unit =
      _curr.onComplete

    override def onAtomComplete(using ctx: TaskContextImpl[T, U, Nothing, Nothing]): Unit =
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

    end onAtomComplete
  end Stepper // case class

  private[portals] sealed trait Steppers[T, U](val task: GenericTask[T, U, Nothing, Nothing])
  private[portals] case class Step[T, U](override val task: GenericTask[T, U, Nothing, Nothing])
      extends Steppers[T, U](task)
  private[portals] case class Loop[T, U](override val task: GenericTask[T, U, Nothing, Nothing], count: Int)
      extends Steppers[T, U](task)

  extension [T, U](task: GenericTask[T, U, Nothing, Nothing]) {

    /** Behavior factory for taking steps over atoms. This will execute the provided `_task` for the following atom. */
    def withStep(_task: GenericTask[T, U, Nothing, Nothing]): GenericTask[T, U, Nothing, Nothing] = task match
      case stepper: Stepper[T, U] => stepper.copy(steppers = stepper.steppers :+ Step(_task))
      case _ => Stepper(List(Step(task), Step(_task)))

    /** Behavior factory for looping behaviors over atoms. This will execute the provided `_task` for the following
      * `count` atoms.
      */
    def withLoop(count: Int)(_task: GenericTask[T, U, Nothing, Nothing]): GenericTask[T, U, Nothing, Nothing] =
      task match
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
  private[portals] abstract class WithAndThenContext[T, U] extends TaskContextImpl[T, U, Nothing, Nothing]:
    var emitted: Seq[U]
    def reset(): Unit

  private[portals] def fromTaskContext[T, U](_ctx: TaskContext[T, U, Nothing, Nothing]) =
    new WithAndThenContext[T, U]() {
      var emitted: Seq[U] = Seq.empty[U]
      override def reset(): Unit = emitted = Seq.empty[U]
      override val state: TaskState[Any, Any] = _ctx.state
      override def emit(event: U) = emitted = emitted :+ event
      override def log: Logger = _ctx.log
    }
  end fromTaskContext // def

  extension [T, U](task: GenericTask[T, U, Nothing, Nothing]) {

    /** Chaining a task with another `_task`, the tasks will share state. */
    def withAndThen[TT](_task: GenericTask[U, TT, Nothing, Nothing]): GenericTask[T, TT, Nothing, Nothing] =
      InitTask[T, TT, Nothing, Nothing] { ctx =>
        val _ctx = fromTaskContext(ctx).asInstanceOf[WithAndThenContext[T, U]]
        ProcessorTask[T, TT] { ctx => event =>
          task.onNext(using _ctx)(event)
          _ctx.emitted.foreach { event =>
            _task.onNext(using ctx.asInstanceOf[TaskContextImpl[U, TT, Nothing, Nothing]])(event)
          }
          _ctx.reset()
        }
      }

    // // Optionally, if there are issues with nested inits:
    // def withAndThen[TT](_task: GenericTask[U, TT, Nothing, Nothing]): GenericTask[T, TT, Nothing, Nothing] =
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

  extension [T, U](task: GenericTask[T, U, Nothing, Nothing]) {

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
    def withWrapper(
        f: ProcessorTaskContext[T, U] ?=> (ProcessorTaskContext[T, U] ?=> T => Unit) => T => Unit
    ): GenericTask[T, U, _, _] =
      ProcessorTask[T, U] { ctx => event => f(using ctx)(task.onNext(using ctx))(event) }

    // Optionally, wrapping around a full task.
    // /** f: ctx => wrapped => task */
    // def withWrapper(f: TaskContext[T, U] ?=> Task[T, U] => Task[T, U]): Task[T, U] =
    //   Tasks.init[T, U] { ctx ?=> f(using ctx)(task) }
  }
end WithWrapperExtension
export WithWrapperExtension.*
