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

  /** behavior factory for map */
  def map[T, U](f: MapTaskContext[T, U] ?=> T => U): GenericTask[T, U, Nothing, Nothing] =
    MapTask[T, U](ctx => f(using ctx))

  /** behavior factory for modifying the key context */
  private[portals] def key[T](f: T => Int): GenericTask[T, T, Nothing, Nothing] =
    ShuffleTask[T, T] { ctx => x =>
      ctx.key = Key(f(x))
      ctx.emit(x)
    }

  //////////////////////////////////////////////////////////////////////////////
  // Portals Task Factories
  //////////////////////////////////////////////////////////////////////////////

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
end Tasks // object
