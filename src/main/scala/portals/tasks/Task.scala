package portals

private[portals] trait Task[I, O]:
  def onNext(ctx: TaskContext[I, O])(t: I): Task[I, O]

  def onError(ctx: TaskContext[I, O])(t: Throwable): Task[I, O]

  def onComplete(ctx: TaskContext[I, O]): Task[I, O]

  def onAtomComplete(ctx: TaskContext[I, O]): Task[I, O]

  // we use `_` underscore here, as otherwise copy is overridden by inheriting case classes
  private[portals] def _copy(
      _onNext: TaskContext[I, O] => I => Task[I, O] = onNext,
      _onError: TaskContext[I, O] => Throwable => Task[I, O] = onError,
      _onComplete: TaskContext[I, O] => Task[I, O] = onComplete,
      _onAtomComplete: TaskContext[I, O] => Task[I, O] = onAtomComplete
  ): Task[I, O] =
    new Task[I, O] {
      override def onNext(ctx: TaskContext[I, O])(t: I): Task[I, O] = _onNext(ctx)(t)
      override def onError(ctx: TaskContext[I, O])(t: Throwable): Task[I, O] = _onError(ctx)(t)
      override def onComplete(ctx: TaskContext[I, O]): Task[I, O] = _onComplete(ctx)
      override def onAtomComplete(ctx: TaskContext[I, O]): Task[I, O] = _onAtomComplete(ctx)
    }
