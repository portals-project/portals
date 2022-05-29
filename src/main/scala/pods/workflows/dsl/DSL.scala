package pods.workflows

object DSL:
  def ctx[I, O](using TaskContext[I, O]): TaskContext[I, O] = summon[TaskContext[I, O]]

  extension [T](ic: IStreamRef[T]) {
    def ![I, O](event: T) = ic.submit(event)
  }
