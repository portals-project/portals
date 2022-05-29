package pods.workflows

private[pods] trait TaskBehavior[I, O]:
  def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O]

  def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O]
  
  def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O]
  
  def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O]


private[pods] class TaskBehaviorUnimpl[I, O] extends TaskBehavior[I, O]:
  override def onNext(ctx: TaskContext[I, O])(t: I): TaskBehavior[I, O] = ???

  override def onError(ctx: TaskContext[I, O])(t: Throwable): TaskBehavior[I, O] = ???
  
  override def onComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] = ???
  
  override def onAtomComplete(ctx: TaskContext[I, O]): TaskBehavior[I, O] = ???