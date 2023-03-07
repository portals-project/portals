package portals.api.dsl

import scala.annotation.experimental

import portals.api.builder.TaskBuilder
import portals.application.*
import portals.application.task.AskerTaskContext
import portals.application.task.GenericTask
import portals.application.task.ProcessorTaskContext
import portals.application.task.ReplierTaskContext

/** Custom task used for extending and writing a task in object oriented style.
  *
  * @tparam T
  *   type of the input event
  * @tparam U
  *   type of the output event
  * @tparam Req
  *   type of the request
  * @tparam Rep
  *   type of the response
  */
@experimental private[portals] sealed trait CustomTask[T, U, Req, Rep]

/** Custom task for writing a replier task in object oriented style. */
@experimental trait CustomReplierTask[T, U, Req, Rep] extends CustomTask[T, U, Req, Rep]:
  def onAsk(using ctx: ReplierTaskContext[T, U, Req, Rep])(t: Req): Unit = ()
  def onNext(using ctx: ProcessorTaskContext[T, U])(t: T): Unit = ()
  def onError(using ctx: ProcessorTaskContext[T, U])(t: Throwable): Unit = ()
  def onComplete(using ctx: ProcessorTaskContext[T, U]): Unit = ()
  def onAtomComplete(using ctx: ProcessorTaskContext[T, U]): Unit = ()

/** Custom task for writing an asker task in object oriented style. */
@experimental trait CustomAskerTask[T, U, Req, Rep] extends CustomTask[T, U, Req, Rep]:
  def onNext(using ctx: AskerTaskContext[T, U, Req, Rep])(t: T): Unit = ()
  def onError(using ctx: AskerTaskContext[T, U, Req, Rep])(t: Throwable): Unit = ()
  def onComplete(using ctx: AskerTaskContext[T, U, Req, Rep]): Unit = ()
  def onAtomComplete(using ctx: AskerTaskContext[T, U, Req, Rep]): Unit = ()

/** Custom task for writing a processor task in object oriented style. */
@experimental trait CustomProcessorTask[T, U] extends CustomTask[T, U, Nothing, Nothing]:
  def onNext(using ctx: ProcessorTaskContext[T, U])(t: T): Unit = ()
  def onError(using ctx: ProcessorTaskContext[T, U])(t: Throwable): Unit = ()
  def onComplete(using ctx: ProcessorTaskContext[T, U]): Unit = ()
  def onAtomComplete(using ctx: ProcessorTaskContext[T, U]): Unit = ()

/** Factory methods for creating the tasks from the custom tasks. */
@experimental object CustomTask:
  /** Creates a replier task from the custom replier task.
    *
    * @param f
    *   function that creates the custom replier task
    * @return
    *   replier task
    */
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

  /** Creates a replier task from the custom replier task.
    *
    * @param f
    *   function that creates the custom replier task
    * @return
    *   replier task
    */
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

  /** Creates a processor task from the custom processor task.
    *
    * @param f
    *   function that creates the custom processor task
    * @return
    *   processor task
    */
  def processor[T, U, X <: CustomProcessorTask[T, U]](
      f: () => X
  ): GenericTask[T, U, Nothing, Nothing] =
    lazy val inner = f()
    TaskBuilder
      .processor[T, U] { event =>
        inner.onNext(event)
      }
