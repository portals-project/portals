package portals

private[portals] trait TaskBehavior[I, O]:
  def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O]

  def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O]
  
  def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O]
  
  def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O]
