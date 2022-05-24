package pods.workflows

object DSL:
  def ctx[I, O](using TaskContext[I, O]): TaskContext[I, O] =
    summon[TaskContext[I, O]]

  extension [T](ic: IStream[T]) {
    def ![I, O](event: T)(using ctx: TaskContext[I, O]) = ctx.send(ic, event)
  }
