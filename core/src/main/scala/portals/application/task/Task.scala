package portals.application.task

import portals.application.*
import portals.application.task.ReplierTaskContext
import portals.application.task.TaskContextImpl
import portals.application.task.TaskExecution

/** Internal API. For internal use only! Applies to this whole file. */
private[portals] sealed trait GenericTask[T, U, Req, Rep]:
  def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit
  def onError(using ctx: TaskContextImpl[T, U, Req, Rep])(t: Throwable): Unit
  def onComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit
  def onAtomComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit

  // we use `_` underscore here, as otherwise copy is overridden by inheriting case classes
  private[portals] def _copy(
      _onNext: TaskContextImpl[T, U, Req, Rep] ?=> T => Unit = onNext,
      _onError: TaskContextImpl[T, U, Req, Rep] ?=> Throwable => Unit = onError,
      _onComplete: TaskContextImpl[T, U, Req, Rep] ?=> Unit = onComplete,
      _onAtomComplete: TaskContextImpl[T, U, Req, Rep] ?=> Unit = onAtomComplete
  ): GenericTask[T, U, Req, Rep] =
    new GenericTask[T, U, Req, Rep] {
      override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = _onNext(using ctx)(t)
      override def onError(using ctx: TaskContextImpl[T, U, Req, Rep])(t: Throwable): Unit = _onError(using ctx)(t)
      override def onComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = _onComplete(using ctx)
      override def onAtomComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = _onAtomComplete(using ctx)
    }
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

private[portals] case class MapTask[T, U](f: TaskContextImpl[T, U, _, _] => T => U)
    extends BaseTask[T, U, Nothing, Nothing]:
  override def onNext(using ctx: TaskContextImpl[T, U, Nothing, Nothing])(t: T): Unit = ctx.emit(f(ctx)(t))
end MapTask // trait

private[portals] case class ProcessorTask[T, U](f: TaskContextImpl[T, U, Nothing, Nothing] => T => Unit)
    extends BaseTask[T, U, Nothing, Nothing]:
  override def onNext(using ctx: TaskContextImpl[T, U, Nothing, Nothing])(t: T): Unit = f(ctx)(t)
end ProcessorTask // trait

private[portals] case class ShuffleTask[T, U](f: TaskContextImpl[T, U, Nothing, Nothing] => T => Unit)
    extends BaseTask[T, U, Nothing, Nothing]:
  override def onNext(using ctx: TaskContextImpl[T, U, Nothing, Nothing])(t: T): Unit = f(ctx)(t)
end ShuffleTask // trait

private[portals] trait ExtensibleTask[T, U, Req, Rep] extends GenericTask[T, U, Req, Rep]
end ExtensibleTask // trait

private[portals] sealed trait AskerTaskKind[T, U, Req, Rep] extends BaseTask[T, U, Req, Rep]

private[portals] case class AskerTask[T, U, Req, Rep](f: TaskContextImpl[T, U, Req, Rep] => T => Unit)(
    val portals: AtomicPortalRefKind[Req, Rep]*
) extends AskerTaskKind[T, U, Req, Rep]:
  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = f(ctx)(t)
end AskerTask // trait

private[portals] sealed trait ReplierTaskKind[T, U, Req, Rep] extends BaseTask[T, U, Req, Rep]

private[portals] case class ReplierTask[T, U, Req, Rep](
    f1: TaskContextImpl[T, U, Req, Rep] => T => Unit,
    f2: TaskContextImpl[T, U, Req, Rep] => Req => Unit
)(val portals: AtomicPortalRefKind[Req, Rep]*)
    extends ReplierTaskKind[T, U, Req, Rep]:
  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = f1(ctx)(t)
end ReplierTask // trait

private[portals] case class AskerReplierTask[T, U, Req, Rep](
    f1: TaskContextImpl[T, U, Req, Rep] => T => Unit,
    f2: TaskContextImpl[T, U, Req, Rep] => Req => Unit
)(
    val askerportals: AtomicPortalRefKind[Req, Rep]*
)(
    val replyerportals: AtomicPortalRefKind[Req, Rep]*
) extends AskerTaskKind[T, U, Req, Rep]
    with ReplierTaskKind[T, U, Req, Rep]:
  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = f1(ctx)(t)
end AskerReplierTask // trait

private[portals] case class IdentityTask[T]() extends BaseTask[T, T, Nothing, Nothing]:
  override def onNext(using ctx: TaskContextImpl[T, T, Nothing, Nothing])(event: T): Unit = ctx.emit(event)
end IdentityTask // case class

// TODO: initialization should happen automatically using the initfactory
private[portals] case class InitTask[T, U, Req, Rep](
    initFactory: TaskContextImpl[T, U, Req, Rep] => GenericTask[T, U, Req, Rep]
) extends BaseTask[T, U, Req, Rep]:
  // wrapped initialized task
  var _task: Option[GenericTask[T, U, Req, Rep]] = None

  // initialize the task, or get the already initialized task
  private def prepOrGet(using ctx: TaskContextImpl[T, U, Req, Rep]): GenericTask[T, U, Req, Rep] =
    _task match
      case Some(task) => task
      case None =>
        _task = Some(TaskExecution.prepareTask(this, ctx))
        _task.get

  override def onNext(using ctx: TaskContextImpl[T, U, Req, Rep])(t: T): Unit = this.prepOrGet.onNext(t)
  override def onError(using ctx: TaskContextImpl[T, U, Req, Rep])(t: Throwable): Unit = this.prepOrGet.onError(t)
  override def onComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = this.prepOrGet.onComplete
  override def onAtomComplete(using ctx: TaskContextImpl[T, U, Req, Rep]): Unit = this.prepOrGet.onAtomComplete
end InitTask // case class

// shorthand, consider removing :p
type Task[T, U] = GenericTask[T, U, Nothing, Nothing]
