package portals

object Tasks:
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

  /** behavior factory for emitting the same values as ingested */
  def identity[T]: Task[T, T] =
    Identity[T]()

  /** behavior factory for modifying the key context */
  private[portals] def key[T](f: T => Int): Task[T, T] =
    Process[T, T] { tctx => x =>
      tctx.key = Key(f(x))
      tctx.emit(x)
    }

  /** behavior factory for using the same behavior as previous behavior */
  def same[T, S]: Task[T, S] =
    // same behavior is compatible with previous behavior
    Same.asInstanceOf[Task[T, S]]

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
