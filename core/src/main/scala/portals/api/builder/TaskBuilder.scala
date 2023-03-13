package portals.api.builder

import portals.application.task.AskerTask
import portals.application.task.AskerTaskContext
import portals.application.task.GenericTask
import portals.application.task.IdentityTask
import portals.application.task.InitTask
import portals.application.task.MapTask
import portals.application.task.MapTaskContext
import portals.application.task.ProcessorTask
import portals.application.task.ProcessorTaskContext
import portals.application.task.ReplierTask
import portals.application.task.ReplierTaskContext
import portals.application.task.ShuffleTask
import portals.application.AtomicPortalRefKind
import portals.util.Key

// The TaskBuilder is a trait so that we can extend it, and add extension methods to it.
private sealed trait TaskBuilder

/** Core Task Factories.
  *
  * Create tasks using these factories. A task is a behavior that is executed on
  * one a task of the workflow. It transforms an input `T` to an output `U`. In
  * doing so, the task can `emit` events, and perform other actions as described
  * in its task context.
  *
  * @example
  *   {{{TaskBuilder.processor[String, Int](event => emit(event.length())}}}
  *
  * @example
  *   {{{TaskBuilder.map[String, Int](x => x.length())}}}
  *
  * The created tasks can be used directly for the workflow like the following
  * example.
  *
  * @example
  *   {{{
  * val taskBehavior = TaskBuilder.processor[String, Int](event => emit(event.length()))
  * builder.workflows[String, Int]("workflowName")
  *  .source(stream)
  *  .task(taskBehavior)
  *  .sink()
  *  .freeze()
  *   }}}
  *
  * @see
  *   `portals.api.builder.TaskExtensions` for more extensions.
  */
object TaskBuilder extends TaskBuilder:
  //////////////////////////////////////////////////////////////////////////////
  // Task Factories
  //////////////////////////////////////////////////////////////////////////////

  /** Behavior factory for processing incoming events.
    *
    * The ProcessorTaskContext allows the task to: emit events; acceess state;
    * log messages.
    *
    * @example
    *   {{{TaskBuilder.processor[String, Int](event => emit(event.length())}}}
    *
    * @tparam T
    *   type of the input events
    * @tparam U
    *   type of the output events
    * @param onNext
    *   handler for the incoming events
    * @return
    *   a task behavior
    */
  def processor[T, U](onNext: ProcessorTaskContext[T, U] ?=> T => Unit): GenericTask[T, U, Nothing, Nothing] =
    ProcessorTask[T, U](ctx => onNext(using ctx))

  /** Behavior factory for emitting the same values as ingested.
    *
    * @tparam T
    *   type of the input events
    * @return
    *   the identity task behavior
    */
  def identity[T]: GenericTask[T, T, _, _] =
    IdentityTask[T]()

  /** Behavior factory for initializing the task before any events.
    *
    * Note: this may be **re-executed** more than once, every time that the task
    * is restarted (e.g. after a failure). Emitted events during the
    * initialization phase are ignored.
    *
    * @example
    *   {{{
    * val task = TaskBuilder.init {
    *   val state = new PerKeyState[Int]("state", 0)
    *   TaskBuilder.processor[String, (String, Int)] { s =>
    *     val count = state.get() + 1
    *     state.set(count)
    *     emit( (s, count) )
    *   }}}
    *
    * @tparam T
    *   type of the input events
    * @tparam U
    *   type of the output events
    * @param initFactory
    *   the initialization factory
    * @return
    *   the initialized task behavior
    */
  def init[T, U](
      initFactory: ProcessorTaskContext[T, U] ?=> GenericTask[T, U, Nothing, Nothing]
  ): GenericTask[T, U, Nothing, Nothing] =
    InitTask[T, U, Nothing, Nothing](ctx => initFactory(using ctx))

  /** Behavior factory for map.
    *
    * Map events of type `T` to events of type `U`. Cannot emit events using
    * context, can access state and log.
    *
    * @example
    *   {{{TaskBuilder.map[String, Int](x => x.length())}}}
    *
    * @tparam T
    *   type of the input events
    * @tparam U
    *   type of the output events
    * @param f
    *   the map function
    * @return
    *   the map task behavior
    */
  def map[T, U](f: MapTaskContext[T, U] ?=> T => U): GenericTask[T, U, Nothing, Nothing] =
    MapTask[T, U](ctx => f(using ctx))

  /** Behavior factory for modifying the implicit key with key-extractor `f`.
    *
    * @example
    *   {{{TaskBuilder.key[String](_.hashCode())}}}
    *
    * @tparam T
    *   type of the input events
    * @param f
    *   the key-extractor function
    * @return
    *   the key task behavior
    */
  private[portals] def key[T](f: T => Long): GenericTask[T, T, Nothing, Nothing] =
    ShuffleTask[T, T] { ctx => x =>
      ctx.key = Key(f(x))
      ctx.emit(x)
    }

  //////////////////////////////////////////////////////////////////////////////
  // Portals Task Factories
  //////////////////////////////////////////////////////////////////////////////

  // TODO: deprecate, or move to extensions
  class PortalsTasks[Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*):
    def asker[T, U](f: AskerTaskContext[T, U, Req, Rep] ?=> T => Unit): GenericTask[T, U, Req, Rep] =
      AskerTask[T, U, Req, Rep](ctx => f(using ctx))(portals: _*)

    def replier[T, U](f1: ProcessorTaskContext[T, U] ?=> T => Unit)(
        f2: ReplierTaskContext[T, U, Req, Rep] ?=> Req => Unit
    ): GenericTask[T, U, Req, Rep] =
      ReplierTask[T, U, Req, Rep](ctx => f1(using ctx), ctx => f2(using ctx))(portals: _*)

  // TODO: deprecate, or move to extensions
  extension (t: TaskBuilder) {
    // TODO: deprecate, or move to extensions
    /* Note: the reason we have this extra step via `portal` is to avoid the user having to specify the Req and Rep types. */
    def portal[Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*) =
      new PortalsTasks[Req, Rep](portals: _*)
  }

  /** Behavior factory for an asker task.
    *
    * The asker task transforms events from type `T` to type `U`. In addition to
    * this, the asker task binds to a portal, and can send requests to the
    * portal. It may also await the returned future from this.
    *
    * @example
    *   {{{
    * TaskBuilder
    *   .asker[T, U, Req, Rep](portal)( event =>
    *     val request = Req(event)
    *     val future = ask(portal, request)
    *     await(future) { future.value.get match
    *       case Rep(value) =>
    *         log.info(value.toString())
    *         emit(value)
    *     }
    *   )
    *   }}}
    *
    * @tparam T
    *   type of the input events
    * @tparam U
    *   type of the output events
    * @tparam Req
    *   type of the requests
    * @tparam Rep
    *   type of the replies
    * @param portals
    *   the portals to which the task will send requests
    * @param f
    *   the task behavior
    * @return
    *   the asker task behavior
    */
  def asker[T, U, Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*)(
      f: AskerTaskContext[T, U, Req, Rep] ?=> T => Unit
  ): GenericTask[T, U, Req, Rep] =
    AskerTask[T, U, Req, Rep](ctx => f(using ctx))(portals: _*)

  /** Behavior factory for a replier task.
    *
    * The replier task transforms events from type `T` to type `U`. In addition
    * to this, the replier task binds to a portal, and can receive requests from
    * the portal to which it may reply.
    *
    * @example
    *   {{{
    *  TaskBuilder
    *    .replier[T, U, Req, Rep](portal)( event =>
    *      // handle event as normal
    *      emit(event)
    *    )(request =>
    *      // handle request
    *      val reply = Rep(request)
    *      reply(request) // reply
    *    )
    *   }}}
    *
    * @tparam T
    *   type of the input events
    * @tparam U
    *   type of the output events
    * @tparam Req
    *   type of the requests
    * @tparam Rep
    *   type of the replies
    * @param portals
    *   the portals from which the task will receive requests
    * @param f1
    *   the handler for regular input events
    * @param f2
    *   the handler for requests
    * @return
    *   replier task behavior
    */
  def replier[T, U, Req, Rep](portals: AtomicPortalRefKind[Req, Rep]*)(f1: ProcessorTaskContext[T, U] ?=> T => Unit)(
      f2: ReplierTaskContext[T, U, Req, Rep] ?=> Req => Unit
  ): GenericTask[T, U, Req, Rep] =
    ReplierTask[T, U, Req, Rep](ctx => f1(using ctx), ctx => f2(using ctx))(portals: _*)

end TaskBuilder // object
