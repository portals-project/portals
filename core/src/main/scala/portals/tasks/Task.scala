package portals

private[portals] trait Task[T, U]:

  def onNext(using ctx: TaskContext[T, U])(t: T): Task[T, U]

  def onError(using ctx: TaskContext[T, U])(t: Throwable): Task[T, U]

  def onComplete(using ctx: TaskContext[T, U]): Task[T, U]

  def onAtomComplete(using ctx: TaskContext[T, U]): Task[T, U]

  // we use `_` underscore here, as otherwise copy is overridden by inheriting case classes
  private[portals] def _copy(
      _onNext: TaskContext[T, U] ?=> T => Task[T, U] = onNext,
      _onError: TaskContext[T, U] ?=> Throwable => Task[T, U] = onError,
      _onComplete: TaskContext[T, U] ?=> Task[T, U] = onComplete,
      _onAtomComplete: TaskContext[T, U] ?=> Task[T, U] = onAtomComplete
  ): Task[T, U] =
    new Task[T, U] {
      override def onNext(using ctx: TaskContext[T, U])(t: T): Task[T, U] = _onNext(using ctx)(t)
      override def onError(using ctx: TaskContext[T, U])(t: Throwable): Task[T, U] = _onError(using ctx)(t)
      override def onComplete(using ctx: TaskContext[T, U]): Task[T, U] = _onComplete(using ctx)
      override def onAtomComplete(using ctx: TaskContext[T, U]): Task[T, U] = _onAtomComplete(using ctx)
    }
