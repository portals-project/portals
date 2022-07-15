package portals

object DSL:
  def ctx[I, O](using TaskContext[I, O]): TaskContext[I, O] = summon[TaskContext[I, O]]

  extension [T](ic: IStreamRef[T]) {
    def !(f: DSL.FUSE.type) = 
      ic.fuse()

    def ![I, O](event: T) = 
      ic.submit(event)
  }

  sealed trait Events
  case object FUSE extends Events
