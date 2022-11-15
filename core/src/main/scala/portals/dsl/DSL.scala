package portals

import scala.annotation.experimental

object DSL:
  //////////////////////////////////////////////////////////////////////////////
  // Tasks DSL
  //////////////////////////////////////////////////////////////////////////////

  // shorthands for the TaskContext
  // Here we can use a generic task context instead, and use the return type of
  // the dependent contextual generic task context to obtain a more specific
  // task context type. This can return both a MapTaskContext or the regular
  // TaskContext, e.g.
  def ctx[T, U](using gctx: GenericTaskContext[T, U]): gctx.type =
    gctx

  // shorthands for the TaskContext methods
  def emit[T, U](event: U)(using EmittingTaskContext[T, U]) = summon[EmittingTaskContext[T, U]].emit(event)
  def state[T, U](using StatefulTaskContext[T, U]) = summon[StatefulTaskContext[T, U]].state
  def log[T, U](using LoggingTaskContext[T, U]) = summon[LoggingTaskContext[T, U]].log

  //////////////////////////////////////////////////////////////////////////////
  // Portals DSL
  //////////////////////////////////////////////////////////////////////////////

  def ask[T, U, Req, Rep](using
      ctx: AskerTaskContext[T, U, Req, Rep]
  )(portal: AtomicPortalRefType[Req, Rep])(req: Req): Unit = ctx.ask(portal)(req)

  def reply[T, U, Req, Rep](using
      ctx: ReplierTaskContext[T, U, Req, Rep]
  )(rep: Rep): Unit = ctx.reply(rep)

  def await[T, U, Req, Rep](using
      ctx: AskerTaskContext[T, U, Req, Rep]
  )(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Unit): Unit = ctx.await(future)(f)

  extension [T, U, Req, Rep](portal: AtomicPortalRefType[Req, Rep]) {
    def ask(using
        AskerTaskContext[T, U, Req, Rep]
    )(req: Req): Future[Rep] = ctx.ask(portal)(req)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Builder DSL
  //////////////////////////////////////////////////////////////////////////////

  /** Experimental API. More convenient interface for using the Builder. */
  @experimental
  object BuilderDSL:
    def Registry(using ab: ApplicationBuilder): RegistryBuilder = ab.registry

    def Workflows[T, U](name: String)(using ab: ApplicationBuilder): WorkflowBuilder[T, U] = ab.workflows[T, U](name)

    def Generators(using ab: ApplicationBuilder): GeneratorBuilder = ab.generators

    def Sequencers(using ab: ApplicationBuilder): SequencerBuilder = ab.sequencers

    def Connections(using ab: ApplicationBuilder): ConnectionBuilder = ab.connections

    def Portal[T, R](name: String)(using ab: ApplicationBuilder): AtomicPortalRef[T, R] = ab.portals.portal(name)

    def PortalsApp(name: String)(app: ApplicationBuilder ?=> Unit): Application =
      val builder = ApplicationBuilders.application(name)
      app(using builder)
      builder.build()
  end BuilderDSL

  //////////////////////////////////////////////////////////////////////////////
  // Experimental DSL
  //////////////////////////////////////////////////////////////////////////////

  /** Experimental API. Various mix of experimental API extensions. */
  @experimental
  object ExperimentalDSL:
    extension (gb: GeneratorBuilder) {
      def empty[T]: AtomicGeneratorRef[T] = gb.fromList(List.empty)
    }

    extension [Rep](future: Future[Rep]) {
      def await[T, U, Req](using ctx: AskerTaskContext[T, U, Req, Rep])(
          f: AskerTaskContext[T, U, Req, Rep] ?=> Unit
      ): Unit =
        ctx.await(future)(f)
    }

    def await[T, U, Req, Rep](using
        ctx: AskerTaskContext[T, U, Req, Rep]
    )(future: Future[Rep])(f: AskerTaskContext[T, U, Req, Rep] ?=> Unit): Unit = ctx.await(future)(f)

    /** Used for fecursive functions from the Asker Task. Yes, here we do need to have the AskerTaskContext as an
      * implicit, otherwise it will crash.
      */
    trait RecZ[A, B]:
      def apply[T, U, Req, Rep](
          f: (AskerTaskContext[T, U, Req, Rep] ?=> A => B) => AskerTaskContext[T, U, Req, Rep] ?=> A => B
      ): AskerTaskContext[T, U, Req, Rep] ?=> A => B = f(recz(f))
      // def apply[Req, Rep](
      //     f: (AskerTaskContext[_, _, Req, Rep] ?=> A => B) => AskerTaskContext[_, _, Req, Rep] ?=> A => B
      // ): AskerTaskContext[_, _, Req, Rep] ?=> A => B = f(recz(f))
    def recz[A, B] = new RecZ[A, B] {}

    extension [T, U, CT, CU, Req, Rep](pfb: FlowBuilder[T, U, CT, CU]#PortalFlowBuilder[Req, Rep]) {
      def askerz[CCU](
          f: (AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit) => AskerTaskContext[CU, CCU, Req, Rep] ?=> CU => Unit
      ): FlowBuilder[T, U, CU, CCU] =
        pfb.asker(f(recz(f)))
    }

  end ExperimentalDSL
end DSL
