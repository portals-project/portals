package portals

import scala.annotation.experimental

object DSL:
  //////////////////////////////////////////////////////////////////////////////
  // Task DSL
  //////////////////////////////////////////////////////////////////////////////
  /** Convenient shorthands for using the TaskContext. */

  /** Summon the given TaskContext.
    *
    * This is a generic method, so it can be used with any TaskContext. Here we use a generic task context instead, and
    * use the return type of the dependent contextual generic task context to obtain a more specific task context type.
    * This can for example return a MapTaskContext when used within a MapTask, or a regular ProcessorTaskContext when
    * used within a ProcessorTask.
    *
    * @return
    *   The contextual task context.
    */
  def ctx(using gctx: GenericGenericTaskContext): gctx.type = gctx

  /** Emit an event
    *
    * @param event
    *   The event to emit.
    */
  def emit[T, U](event: U)(using EmittingTaskContext[T, U]) = summon[EmittingTaskContext[T, U]].emit(event)

  /** Task state.
    *
    * @return
    *   The state of the task.
    */
  def state[T, U](using StatefulTaskContext) = summon[StatefulTaskContext].state

  /** Logger.
    *
    * @return
    *   The logger.
    */
  def log[T, U](using LoggingTaskContext[T, U]) = summon[LoggingTaskContext[T, U]].log

  //////////////////////////////////////////////////////////////////////////////
  // Builder DSL
  //////////////////////////////////////////////////////////////////////////////
  extension (gb: GeneratorBuilder) {

    /** Generator with a single output value `sig`.
      *
      * @example
      *   {{{val sig = Generators.signal[Int](1)}}}
      * @param sig
      *   The output value.
      */
    def signal[T](sig: T): AtomicGeneratorRef[T] = gb.fromList(List[T](sig))
  }

  //////////////////////////////////////////////////////////////////////////////
  // Portals DSL
  //////////////////////////////////////////////////////////////////////////////
  /** Convenient shorthands for using the portals, askers, repliers, awaiters. */

  /** Ask the `portal` with `req`, returns a future of the reply.
    *
    * @param portal
    *   The portal to ask.
    * @param req
    *   The request to send.
    * @return
    *   A future of the reply.
    */
  def ask[T, U, Req, Rep](using ctx: AskerTaskContext[T, U, Req, Rep])(portal: AtomicPortalRefType[Req, Rep])(
      req: Req
  ): Future[Rep] =
    ctx.ask(portal)(req)

  /** Reply with `rep` to the handled request.
    *
    * @param rep
    *   The reply to send.
    */
  def reply[T, U, Req, Rep](using
      ctx: ReplierTaskContext[T, U, Req, Rep]
  )(rep: Rep): Unit = ctx.reply(rep)

  /** Await the completion of the `future` and then execute continuation `f`.
    *
    * @param future
    *   The future to await.
    * @param f
    *   The function to execute.
    */
  def await[T, U, Req, Rep](using
      ctx: AskerTaskContext[T, U, Req, Rep]
  )(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Unit): Unit = ctx.await(future)(f)

  extension [Rep](future: Future[Rep]) {
    def await[T, U, Req](using ctx: AskerTaskContext[T, U, Req, Rep])(
        f: AskerTaskContext[T, U, Req, Rep] ?=> Unit
    ): Unit =
      ctx.await(future)(f)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Convenient Builder DSL
  //////////////////////////////////////////////////////////////////////////////
  /** Convenient interface for using the ApplicationBuilder. */

  /** Summon the RegistryBuilder. */
  def Registry(using ab: ApplicationBuilder): RegistryBuilder = ab.registry

  /** Summon the WorkflowBuilder. */
  def Workflows[T, U](name: String)(using ab: ApplicationBuilder): WorkflowBuilder[T, U] = ab.workflows[T, U](name)

  /** Summon the GeneratorBuilder. */
  def Generators(name: String)(using ab: ApplicationBuilder): GeneratorBuilder = ab.generators(name)

  /** Summon the GeneratorBuilder. */
  def Generators(using ab: ApplicationBuilder): GeneratorBuilder = ab.generators

  /** Summon the SequencerBuilder. */
  def Sequencers(name: String)(using ab: ApplicationBuilder): SequencerBuilder = ab.sequencers(name)

  /** Summon the SequencerBuilder. */
  def Sequencers(using ab: ApplicationBuilder): SequencerBuilder = ab.sequencers

  /** Summon the SplitterBuilder. */
  def Splitters(name: String)(using ab: ApplicationBuilder): SplitterBuilder = ab.splitters(name)

  /** Summon the SplitterBuilder. */
  def Splitters(using ab: ApplicationBuilder): SplitterBuilder = ab.splitters

  /** Summon the SplitBuilder. */
  def Splits(using ab: ApplicationBuilder): SplitBuilder = ab.splits

  /** Summon the ConnectionBuilder. */
  def Connections(using ab: ApplicationBuilder): ConnectionBuilder = ab.connections

  /** Summon the PortalBuilder. */
  def Portal[T, R](name: String)(using ab: ApplicationBuilder): AtomicPortalRef[T, R] = ab.portals.portal(name)

  /** Summon the PortalBuilder. */
  def Portal[T, R](name: String, f: T => Long)(using ab: ApplicationBuilder): AtomicPortalRef[T, R] =
    ab.portals.portal(name, f)

  /** Build a new PortalsApplication.
    *
    * @example
    *   {{{
    * val myApp = PortalsApp("myApp") {
    *   val generator = Generators.signal("hello world")
    *   val workflow = Workflows[Int, Int]()
    *     .source(generator.stream)
    *     .map(_ + 1)
    *     .sink()
    *     .freeze()
    * }
    *   }}}
    *
    * @param name
    *   The name of the application.
    * @param app
    *   The application factory (see example).
    * @return
    *   The Portals application.
    */
  def PortalsApp(name: String)(app: ApplicationBuilder ?=> Unit): Application =
    val builder = ApplicationBuilders.application(name)
    app(using builder)
    builder.build()
end DSL // object
