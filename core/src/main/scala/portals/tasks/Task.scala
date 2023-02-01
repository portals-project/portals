// format: off
package portals

/** Internal API. For internal use only! Applies to this whole file. */
private[portals] sealed trait GenericTask[T, U, Req, Rep]:
  def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit
  def onError(using ctx: TaskContextImpl[T, U, Req, Rep])(t: Throwable): Unit
  def onComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit
  def onAtomComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit
end GenericTask // trait

private[portals] sealed trait TaskUnimpl[T, U, Req, Rep] extends GenericTask[T, U, Req, Rep]:
  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = ???
  override def onError(using ctx: TaskContextImpl[T, U, Req, Rep])(t: Throwable): Unit = ???
  override def onComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = ???
  override def onAtomComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = ???
end TaskUnimpl // trait

private[portals] sealed trait BaseTask[T, U, Req, Rep] extends GenericTask[T, U, Req, Rep]:
  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = ()
  override def onError(using ctx: TaskContextImpl[T, U, Req, Rep])(t: Throwable): Unit = ()
  override def onComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = ()
  override def onAtomComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = ()
end BaseTask // trait

private[portals] case class MapTask[T, U](f: TaskContextImpl[T, U, _, _] => T => U) extends BaseTask[T, U, Nothing, Nothing]:
  override def onNext(using ctx: TaskContextImpl[T, U, Nothing, Nothing])(t: T): Unit = ctx.emit(f(ctx)(t))
end MapTask // trait

private[portals] case class ProcessorTask[T, U](f: TaskContextImpl[T, U, Nothing, Nothing] => T => Unit) extends BaseTask[T, U, Nothing, Nothing]:
  override def onNext(using ctx: TaskContextImpl[T, U, Nothing, Nothing])(t: T): Unit = f(ctx)(t)
end ProcessorTask // trait

private[portals] trait ExtensibleTask[T, U] extends GenericTask[T, U, Nothing, Nothing]
end ExtensibleTask // trait

private[portals] case class AskerTask[T, U, Req, Rep](f: TaskContextImpl[T, U, Req, Rep] => T => Unit)(val portals: AtomicPortalRefType[Req, Rep]*) extends BaseTask[T, U, Req, Rep]:
  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = f(ctx)(t)
end AskerTask // trait

private[portals] case class ReplierTask[T, U, Req, Rep](f1: TaskContextImpl[T, U, Req, Rep] => T => Unit, f2: ReplierTaskContext[T, U, Req, Rep] => Req => Unit)(val portals: AtomicPortalRefType[Req, Rep]*) extends BaseTask[T, U, Req, Rep]:
  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = f1(ctx)(t)
end ReplierTask // trait

private[portals] case class IdentityTask[T]() extends BaseTask[T, T, Nothing, Nothing]:
  override def onNext(using ctx: TaskContextImpl[T, T, Nothing, Nothing])(event: T): Unit = ctx.emit(event)
end IdentityTask // case class

// TODO: initialization should happen automatically using the initfactory
private[portals] case class InitTask[T, U, Req, Rep](initFactory: TaskContextImpl[T, U, Req, Rep] => GenericTask[T, U, Req, Rep]) extends BaseTask[T, U, Req, Rep]:

  // wrapped initialized task
  var _task: Option[GenericTask[T, U, Req, Rep]] = None

  // initialize the task, or get the already initialized task
  private def prepOrGet(using ctx: TaskContextImpl[T, U, Req, Rep]): GenericTask[T, U, Req, Rep] =
    _task match
      case Some(task) => task
      case None =>
        _task = Some(TaskXX.prepareTask(this, ctx))
        _task.get

  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = this.prepOrGet.onNext(t)
  override def onError(using ctx: TaskContextImpl[T, U, Req, Rep])(t: Throwable): Unit = this.prepOrGet.onError(t)
  override def onComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = this.prepOrGet.onComplete
  override def onAtomComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = this.prepOrGet.onAtomComplete
end InitTask // case class

object TaskXX:
  /** Prepare a task behavior at runtime. This executes the initialization and returns the initialized task. This needs to be called internally to initialize the task behavior before execution.
    */
  private[portals] def prepareTask[T, U, Req, Rep](task: GenericTask[T, U, Req, Rep], ctx: TaskContextImpl[T, U, Req, Rep]): GenericTask[T, U, Req, Rep] =
    /** internal recursive method */
    def prepareTaskRec(task: GenericTask[T, U, Req, Rep], ctx: TaskContextImpl[T, U, Req, Rep]): GenericTask[T, U, Req, Rep] =
      // FIXME: (low prio) this will fail if we have a init nested inside of a normal behavior
      task match
        case InitTask(initFactory) => prepareTaskRec(initFactory(ctx), ctx)
        case _ => task

    /** prepare the task, recursively performing initialization */
    prepareTaskRec(task, ctx)
  end prepareTask // def


  private[portals] def run_and_cleanup_reply[T, U, Req, Rep](id: Int, r: Rep)(using
      actx: AskerTaskContext[T, U, Req, Rep]
  ): Unit =
    lazy val _futures: AskerTaskContext[_, _, _, Rep] ?=> PerTaskState[Map[Int, Rep]] =
      PerTaskState[Map[Int, Rep]]("futures", Map.empty)
    lazy val _continuations: AskerTaskContext[T, U, Req, Rep] ?=> PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]] =
      PerTaskState[Map[Int, Continuation[T, U, Req, Rep]]]("continuations", Map.empty)
    // set future
    _futures.update(id, r)
    // run continuation
    _continuations.get(id).get(using actx)
    // cleanup future
    _futures.remove(id)
    // cleanup continuation
    _continuations.remove(id)
  end run_and_cleanup_reply // def

// format: on
