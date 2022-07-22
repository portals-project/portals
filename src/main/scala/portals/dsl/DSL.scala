package portals

object DSL:

  //////////////////////////////////////////////////////////////////////////////
  // Tasks
  //////////////////////////////////////////////////////////////////////////////

  // shorthands for the TaskContext
  def ctx[T, U](using gctx: GenericTaskContext[T, U]): gctx.type =
    // here we can use a generic task context instead, and use the return type of
    // the dependent contextual generic task context to obtain a more specific
    // task context type. This should work.
    gctx

  // shorthands for the TaskContext methods
  def emit[T, U](event: U)(using EmittingTaskContext[T, U]) = summon[EmittingTaskContext[T, U]].emit(event)
  def state[T, U](using StatefulTaskContext[T, U]) = summon[StatefulTaskContext[T, U]].state
  def log[T, U](using LoggingTaskContext[T, U]) = summon[LoggingTaskContext[T, U]].log

  //////////////////////////////////////////////////////////////////////////////
  // Portals
  //////////////////////////////////////////////////////////////////////////////

  def ask[T, U, Req, Rep, Portals <: AtomicPortalRefType[Req, Rep]](using
      ctx: AskerTaskContext[T, U, Req, Rep, Portals]
  )(portal: Portals)(req: Req): Unit = ctx.ask(portal)(req)

  def reply[T, U, Req, Rep, Portals <: AtomicPortalRefType[Req, Rep]](using
      ctx: ReplierTaskContext[T, U, Req, Rep, Portals]
  )(rep: Rep): Unit = ctx.reply(rep)

  def await[T, U, Req, Rep, Portals <: AtomicPortalRefType[Req, Rep]](using
      ctx: AskerTaskContext[T, U, Req, Rep, Portals]
  )(future: Future[Rep])(f: Option[Rep] => Task[T, U]): Task[T, U] = ctx.await(future)(f)

  extension [T, U, Req, Rep, Portals <: (Singleton & AtomicPortalRefType[Req, Rep])](using
      AskerTaskContext[T, U, Req, Rep, Portals]
  )(portal: Portals) {
    def ask(req: Req): Future[Rep] = ctx.ask(portal)(req)
  }

  extension [T, U, Req, Rep, Portals <: (Singleton & AtomicPortalRefType[Req, Rep])](using
      ReplierTaskContext[T, U, Req, Rep, Portals]
  )(portal: Portals) {
    def reply(rep: Rep): Unit = ctx.reply(rep)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Refs
  //////////////////////////////////////////////////////////////////////////////

  sealed trait Events
  case object FUSE extends Events

  extension [T](ic: IStreamRef[T]) {
    def !(f: DSL.FUSE.type) =
      ic.fuse()

    def !(event: T) =
      ic.submit(event)
  }
end DSL // object
