package pods.workflows

object TaskBehaviors:
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
  def map[I, O](f: I => O): TaskBehavior[I, O] =
    ProcessBehavior[I, O](tctx => x => tctx.emit(f(x)))

  /** behavior factory for flatMap */
  def flatMap[I, O](f: I => TraversableOnce[O]): TaskBehavior[I, O] =
    ProcessBehavior[I, O](tctx => x => f(x).foreach(tctx.emit(_)))

  /** behavior factory for using the same behavior as previous behavior */
  def same[T, S]: TaskBehavior[T, S] =
    // same behavior is compatible with previous behavior
    SameBehavior.asInstanceOf[TaskBehavior[T, S]]

  /** behavior factory for emitting the same values as ingested */
  def identity[T]: TaskBehavior[T, T] =
    IdentityBehavior[T]()

  private[pods] case class VSMBehavior[I, O](
      _onNext: TaskContext[I, O] => I => TaskBehavior[I, O]
  ) extends TaskBehavior[I, O]:
    override def onNext(tctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] = _onNext(tctx)(t)

  private[pods] case class ProcessBehavior[I, O](
      _onNext: TaskContext[I, O] => I => Unit
  ) extends TaskBehavior[I, O]:
    override def onNext(tctx: TaskContext[I, O])(event: I): TaskBehavior[I, O] =
      _onNext(tctx)(event)
      TaskBehaviors.same

  private[pods] case object SameBehavior extends TaskBehavior[Nothing, Nothing]
  // this is fine, the methods are ignored as we reuse the previous behavior

  private[pods] case class IdentityBehavior[T]() extends TaskBehavior[T, T]:
    override def onNext(tctx: TaskContext[T, T])(event: T): TaskBehavior[T, T] =
      tctx.emit(event)
      TaskBehaviors.same
