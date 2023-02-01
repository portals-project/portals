package portals

private sealed trait Tasks

/** Core Task Factories. */
object Tasks extends Tasks:
  //////////////////////////////////////////////////////////////////////////////
  // Task Factories
  //////////////////////////////////////////////////////////////////////////////
  /** Behavior factory for handling incoming event and context. */
  def processor[T, U](onNext: ProcessorTaskContext[T, U] ?=> T => Unit): GenericTask[T, U, Nothing, Nothing] =
    ProcessorTask[T, U](ctx => onNext(using ctx))

  /** Behavior factory for emitting the same values as ingested. */
  def identity[T]: GenericTask[T, T, _, _] =
    IdentityTask[T]()

  /** Behavior factory for initializing the task before any events.
    *
    * Note: this may be **re-executed** more than once, every time that the task is restarted (e.g. after a failure).
    */
  def init[T, U](
      initFactory: ProcessorTaskContext[T, U] ?=> GenericTask[T, U, Nothing, Nothing]
  ): GenericTask[T, U, Nothing, Nothing] =
    InitTask[T, U, Nothing, Nothing](ctx => initFactory(using ctx))

////////////////////////////////////////////////////////////////////////////////
// Task Extensions
////////////////////////////////////////////////////////////////////////////////
/** Task Extensions. */
object TaskExtensions:
  extension (t: Tasks) {

    /** behavior factory for map */
    def map[T, U](f: MapTaskContext[T, U] ?=> T => U): GenericTask[T, U, Nothing, Nothing] =
      MapTask[T, U](ctx => f(using ctx))

    /** behavior factory for flatMap */
    def flatMap[T, U](f: MapTaskContext[T, U] ?=> T => TraversableOnce[U]): GenericTask[T, U, Nothing, Nothing] =
      ProcessorTask[T, U] { ctx => x => f(using ctx)(x).foreach(ctx.emit) }

    /** behavior factory for filter */
    def filter[T](p: T => Boolean): GenericTask[T, T, Nothing, Nothing] =
      ProcessorTask[T, T] { ctx => event =>
        if (p(event)) ctx.emit(event)
      }

    /** behavior factory for modifying the key context */
    private[portals] def key[T](f: T => Int): GenericTask[T, T, Nothing, Nothing] =
      ProcessorTask[T, T] { ctx => x =>
        ctx.key = Key(f(x))
        ctx.emit(x)
      }
  }
end TaskExtensions
export TaskExtensions.*

// //////////////////////////////////////////////////////////////////////////////
// // Behavior Modifying Combinators
// //////////////////////////////////////////////////////////////////////////////
// /** Task Behavior Combinators. */
// object TaskBehaviorCombinators:
//   extension [T, U, Req, Rep](task: GenericTask[T, U, Req, Rep]) {
//     def withOnNext(f: ProcessorTaskContext[T, U] ?=> T => Unit): GenericTask[T, U, Req, Rep] =
//       task._copy(_onNext = f)

//     def withOnError(f: ProcessorTaskContext[T, U] ?=> Throwable => Unit): GenericTask[T, U, Req, Rep] =
//       task._copy(_onError = f)

//     def withOnComplete(f: ProcessorTaskContext[T, U] ?=> Unit): GenericTask[T, U, Req, Rep] =
//       task._copy(_onComplete = f)

//     def withOnAtomComplete(f: ProcessorTaskContext[T, U] ?=> Unit): GenericTask[T, U, Req, Rep] =
//       task._copy(_onAtomComplete = f)
//   }
// end TaskBehaviorCombinators
// export TaskBehaviorCombinators.*

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

// ////////////////////////////////////////////////////////////////////////////////
// // WithAndThen Extension
// ////////////////////////////////////////////////////////////////////////////////
// /** WithAndThen Extension. */
// object WithAndThenExtension:
//   private[portals] trait WithAndThenContext[T, U] extends TaskContext[T, U]:
//     var emitted: Seq[U]
//     def reset(): Unit

//   def fromTaskContext[T, U](ctx: TaskContext[T, U]) =
//     new WithAndThenContext[T, U] {
//       var emitted: Seq[U] = Seq.empty[U]
//       override def reset(): Unit = emitted = Seq.empty[U]
//       override def state: TaskState[Any, Any] = _ctx.state
//       override def emit(event: U) = emitted = emitted :+ event
//       override def log: Logger = _ctx.log
//       private[portals] var key: portals.Key[Int] = ctx.key
//       private[portals] var path: String = ctx.path
//       private[portals] var system: portals.PortalsSystem = ctx.system
//       private[portals] var _ctx: TaskContext[T, U] = ctx
//     }
//   end fromTaskContext // def

//   extension [T, U](task: Task[T, U]) {

//     /** Chaining a task with another `_task`, the tasks will share state. */
//     def withAndThen[TT](_task: Task[U, TT]): Task[T, TT] =
//       Tasks.init[T, TT] { ctx ?=>
//         val _ctx = fromTaskContext(ctx).asInstanceOf[WithAndThenContext[T, U]]
//         Tasks.processor[T, TT] { event =>
//           task.onNext(using _ctx.asInstanceOf)(event)
//           _ctx.emitted.foreach { event => _task.onNext(using ctx.asInstanceOf)(event) }
//           _ctx.reset()
//         }
//       }

//     // Optionally, if there are issues with nested inits:
//     // def withAndThen[TT](_task: Task[U, TT]): Task[T, TT] =
//     //   Tasks.processor[T, TT] { ctx ?=> event =>
//     //     val _ctx = fromTaskContext[T, U](ctx.asInstanceOf)
//     //     task.onNext(using _ctx)(event)
//     //     _ctx.emitted.foreach { event => _task.onNext(using ctx.asInstanceOf)(event) }
//     //     _ctx.reset()
//     //   }
//   }
// end WithAndThenExtension
// export WithAndThenExtension.*

////////////////////////////////////////////////////////////////////////////////
// WithWrapper Extension
////////////////////////////////////////////////////////////////////////////////
/** WithWrapper Extension. */
object WithWrapperExtension:

  extension [T, U](task: ProcessorTask[T, U]) {

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

////////////////////////////////////////////////////////////////////////////////
// Portals Extension
////////////////////////////////////////////////////////////////////////////////
/** Portals Extension. */
object PortalsExtension:
  import Tasks.*

  class PortalsTasks[Req, Rep](portals: AtomicPortalRefType[Req, Rep]*):
    def asker[T, U](f: AskerTaskContext[T, U, Req, Rep] ?=> T => Unit): GenericTask[T, U, Req, Rep] =
      AskerTask[T, U, Req, Rep](ctx => f(using ctx))(portals: _*)

    def replier[T, U](f1: ProcessorTaskContext[T, U] ?=> T => Unit)(
        f2: ReplierTaskContext[T, U, Req, Rep] ?=> Req => Unit
    ): GenericTask[T, U, Req, Rep] =
      ReplierTask[T, U, Req, Rep](ctx => f1(using ctx), ctx => f2(using ctx))(portals: _*)

  extension (t: Tasks) {
    /* Note: the reason we have this extra step via `portal` is to avoid the user having to specify the Req and Rep types. */
    def portal[Req, Rep](portals: AtomicPortalRefType[Req, Rep]*) =
      new PortalsTasks[Req, Rep](portals: _*)
  }

  // private[portals] case class AskerTask[T, U, Req, Rep](
  //     f: AskerTaskContext[T, U, Req, Rep] => T => Unit
  // )(val portals: AtomicPortalRefType[Req, Rep]*)
  //     extends BaseTask[T, U]:

  //   override def onNext(using ctx: TaskContext[T, U])(t: T): Unit = f(ctx.asInstanceOf)(t)

  // private[portals] object AskerTask:
  //   private[portals] def run_and_cleanup_reply[T, U, Req, Rep](id: Int, r: Rep)(using
  //       actx: AskerTaskContext[T, U, Req, Rep]
  //   ): Unit =
  //     // set future
  //     actx._futures.update(id, r)
  //     // run continuation
  //     actx._continuations.get(id).get(using actx)
  //     // cleanup future
  //     actx._futures.remove(id)
  //     // cleanup continuation
  //     actx._continuations.remove(id)

  // private[portals] case class ReplierTask[T, U, Req, Rep](
  //     f1: TaskContext[T, U] => T => Unit,
  //     f2: ReplierTaskContext[T, U, Req, Rep] => Req => Unit
  // )(val portals: AtomicPortalRefType[Req, Rep]*)
  //     extends BaseTask[T, U]:

  //   // // TODO: this is not used, AFAIK by the runtime, either use it or loose it :p.
  //   // def requestingOnNext(using ctx: ReplierTaskContext[T, U, Req, Rep])(req: Req): Unit = f2(ctx)(req)

  //   override def onNext(using ctx: TaskContext[T, U])(t: T): Unit = f1(ctx)(t)

end PortalsExtension
export PortalsExtension.*
