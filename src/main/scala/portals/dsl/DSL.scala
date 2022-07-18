package portals

object DSL:

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

  sealed trait Events
  case object FUSE extends Events

  extension [T](ic: IStreamRef[T]) {
    def !(f: DSL.FUSE.type) =
      ic.fuse()

    def !(event: T) =
      ic.submit(event)
  }
end DSL // object
