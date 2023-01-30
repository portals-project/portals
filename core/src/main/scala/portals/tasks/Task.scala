package portals

private[portals] trait Task[T, U]:

  def onNext(using ctx: TaskContext[T, U])(t: T): Unit

  def onError(using ctx: TaskContext[T, U])(t: Throwable): Unit

  def onComplete(using ctx: TaskContext[T, U]): Unit

  def onAtomComplete(using ctx: TaskContext[T, U]): Unit

  // we use `_` underscore here, as otherwise copy is overridden by inheriting case classes
  private[portals] def _copy(
      _onNext: TaskContext[T, U] ?=> T => Unit = onNext,
      _onError: TaskContext[T, U] ?=> Throwable => Unit = onError,
      _onComplete: TaskContext[T, U] ?=> Unit = onComplete,
      _onAtomComplete: TaskContext[T, U] ?=> Unit = onAtomComplete
  ): Task[T, U] =
    new Task[T, U] {
      override inline def onNext(using ctx: TaskContext[T, U])(t: T): Unit = _onNext(using ctx)(t)
      override inline def onError(using ctx: TaskContext[T, U])(t: Throwable): Unit = _onError(using ctx)(t)
      override inline def onComplete(using ctx: TaskContext[T, U]): Unit = _onComplete(using ctx)
      override inline def onAtomComplete(using ctx: TaskContext[T, U]): Unit = _onAtomComplete(using ctx)
    }
