package portals

import scala.annotation.experimental

import portals.*

@experimental
sealed trait CustomTask[T, U, Req, Rep]:
  type Context
  def onNext(using ctx: Context)(t: T): Unit = ()
  def onError(using ctx: Context)(t: Throwable): Unit = ()
  def onComplete(using ctx: Context): Unit = ()
  def onAtomComplete(using ctx: Context): Unit = ()

@experimental
trait CustomReplierTask[T, U, Req, Rep] extends CustomTask[T, U, Req, Rep]:
  override type Context = ProcessorTaskContext[T, U]
  type ContextReply = ReplierTaskContext[T, U, Req, Rep]
  def onAsk(using ctx: ContextReply)(t: Req): Unit = ()
  override def onNext(using ctx: Context)(t: T): Unit = ()
  override def onError(using ctx: Context)(t: Throwable): Unit = ()
  override def onComplete(using ctx: Context): Unit = ()
  override def onAtomComplete(using ctx: Context): Unit = ()

@experimental
trait CustomAskerTask[T, U, Req, Rep] extends CustomTask[T, U, Req, Rep]:
  override type Context = AskerTaskContext[T, U, Req, Rep]
  override def onNext(using ctx: Context)(t: T): Unit = ()
  override def onError(using ctx: Context)(t: Throwable): Unit = ()
  override def onComplete(using ctx: Context): Unit = ()
  override def onAtomComplete(using ctx: Context): Unit = ()

@experimental
trait CustomProcessorTask[T, U] extends CustomTask[T, U, Nothing, Nothing]:
  override type Context = ProcessorTaskContext[T, U]
  override def onNext(using ctx: Context)(t: T): Unit = ()
  override def onError(using ctx: Context)(t: Throwable): Unit = ()
  override def onComplete(using ctx: Context): Unit = ()
  override def onAtomComplete(using ctx: Context): Unit = ()

@experimental
// TODO: this is a bit strange how it works, but from the API side it is ok.
object CustomTask:
  def asker[T, U, Req, Rep, X <: CustomAskerTask[T, U, Req, Rep]](
      portal: AtomicPortalRef[Req, Rep]
  )(
      f: () => X
  ): GenericTask[T, U, Req, Rep] =
    lazy val inner = f()
    TaskBuilder
      .portal(portal)
      .asker[T, U] { event =>
        inner.onNext(event)
      }

  def replier[T, U, Req, Rep, X <: CustomReplierTask[T, U, Req, Rep]](
      portal: AtomicPortalRef[Req, Rep]
  )(
      f: () => X
  ): GenericTask[T, U, Req, Rep] =
    lazy val inner = f()
    TaskBuilder
      .portal(portal)
      .replier[T, U] { event =>
        inner.onNext(event)
      } { ask =>
        inner.onAsk(ask)
      }

  def processor[T, U, X <: CustomProcessorTask[T, U]](
      f: () => X
  ): GenericTask[T, U, Nothing, Nothing] =
    lazy val inner = f()
    TaskBuilder
      .processor[T, U] { event =>
        inner.onNext(event)
      }
