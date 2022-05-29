package pods.workflows

import java.util.concurrent.Flow.{Subscription => FSubscription}
import java.util.concurrent.Flow.{Subscriber => FSubscriber}

private[pods] object TaskRunner:
  def processorFromTask[I, O](ctx: TaskContext[I, O], behavior: TaskBehavior[I, O]): Processor[I, O] =
    val processor = new Processor[I, O]{
      override def onSubscribe(x: FSubscription): Unit = x.request(Long.MaxValue)
      override def onNext(x: I): Unit = behavior.onNext(ctx)(x)
      override def onError(x: Throwable): Unit = behavior.onError(ctx)(x)
      override def onComplete(): Unit = behavior.onComplete(ctx)
      override def onAtomComplete(id: Long): Unit = behavior.onAtomComplete(ctx)
      override def subscribe(s: FSubscriber[_ >: O]): Unit = ???
    }
    processor

  def run[I, O](behavior: TaskBehavior[I, O])(using system: SystemContext): (IStreamRef[I], OStreamRef[O]) =
    val ctx = TaskContext[I, O]()
    val processor = processorFromTask(ctx, behavior)
    val (executedProcessor, iref, oref) = system.executionContext.execute(processor)
    ctx.submitter = executedProcessor
    ctx.mainiref = iref
    ctx.mainoref = oref
    ctx.key = Key(0)
    ctx.system = system
    ctx._iref = iref
    ctx._oref = oref
    (iref, oref) 
