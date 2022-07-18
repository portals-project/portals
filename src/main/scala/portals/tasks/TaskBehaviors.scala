package portals

object TaskBehaviors:
  //////////////////////////////////////////////////////////////////////////////
  // TaskBehavior Factories
  //////////////////////////////////////////////////////////////////////////////

  /** behavior factory for handling incoming event and context with a virtual state machine */
  def vsm[I, O](
      onNext: TaskContext[I, O] ?=> I => TaskBehavior[I, O]
  ): TaskBehavior[I, O] =
    VSMBehavior[I, O](ctx => onNext(using ctx))

  /** behavior factory for handling incoming event and context */
  def processor[I, O](
      onNext: TaskContext[I, O] ?=> I => Unit
  ): TaskBehavior[I, O] =
    ProcessBehavior[I, O](tctx => onNext(using tctx))

  /** behavior factory for map */
  def map[I, O](f: ReducedTaskContext[I, O] ?=> I => O): TaskBehavior[I, O] =
    // todo: reduce context initialization to once per task with init behavior
    ProcessBehavior[I, O](tctx =>
      x =>
        val ctx = ReducedTaskContext.fromTaskContext(tctx)
        tctx.emit(
          f(using ctx)(x)
        )
    )

  /** behavior factory for flatMap */
  def flatMap[I, O](f: ReducedTaskContext[I, O] ?=> I => TraversableOnce[O]): TaskBehavior[I, O] =
    // todo: reduce context initialization to once per task with init behavior
    ProcessBehavior[I, O](tctx =>
      x =>
        val ctx = ReducedTaskContext.fromTaskContext(tctx)
        f(using ctx)(x).foreach(tctx.emit(_))
    )

  /** behavior factory for emitting the same values as ingested */
  def identity[T]: TaskBehavior[T, T] =
    IdentityBehavior[T]()

  /** behavior factory for using the same behavior as previous behavior */
  def same[T, S]: TaskBehavior[T, S] =
    // same behavior is compatible with previous behavior
    SameBehavior.asInstanceOf[TaskBehavior[T, S]]

  //////////////////////////////////////////////////////////////////////////////
  // TaskBehavior Implementations
  //////////////////////////////////////////////////////////////////////////////

  private[portals] case class VSMBehavior[I, O](
      _onNext: TaskContext[I, O] => I => TaskBehavior[I, O]
  ) extends BaseTaskBehavior[I, O]:
    override def onNext(tctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] =
      _onNext(tctx)(t)

  private[portals] case class ProcessBehavior[I, O](
      _onNext: TaskContext[I, O] => I => Unit
  ) extends BaseTaskBehavior[I, O]:
    override def onNext(tctx: TaskContext[I, O])(event: I): TaskBehavior[I, O] =
      _onNext(tctx)(event)
      TaskBehaviors.same

  private[portals] case class IdentityBehavior[T]() extends BaseTaskBehavior[T, T]:
    override def onNext(tctx: TaskContext[T, T])(event: T): TaskBehavior[T, T] =
      tctx.emit(event)
      TaskBehaviors.same

  private[portals] case object SameBehavior extends TaskBehaviorUnimpl[Nothing, Nothing]
  // this is fine, the methods are ignored as we reuse the previous behavior

  //////////////////////////////////////////////////////////////////////////////
  // Base TaskBehaviors
  //////////////////////////////////////////////////////////////////////////////

  private[portals] class TaskBehaviorUnimpl[I, O] extends TaskBehavior[I, O]:
    override def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] = ???

    override def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O] = ???

    override def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] = ???

    override def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] = ???

  private[portals] class BaseTaskBehavior[I, O] extends TaskBehavior[I, O]:
    override def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] = ???

    // TODO: implement onError and onComplete to have base behavior, forwarding
    // the completion, e.g.
    override def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O] = ???

    override def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] = ???

    override def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] =
      ctx.fuse()
      TaskBehaviors.same
