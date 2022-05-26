package pods.workflows

private[pods] trait TaskBehavior[I, O]:
  def onNext(tctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] = ???
  def onError(tctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O] = ???
  def onComplete(tctx: TaskContext[I, O]): TaskBehavior[I, O] = ???
  // when alignment has been reached
  def onAtomComplete(tctx: TaskContext[I, O]): TaskBehavior[I, O] = ???  // onTick

