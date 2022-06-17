package pods.workflows

import java.util.concurrent.Flow.{Subscription => FSubscription}
import java.util.concurrent.Flow.{Subscriber => FSubscriber}

private[pods] object TaskRunner:
  def run[I, O](behavior: TaskBehavior[I, O])(using system: SystemContext): (IStreamRef[I], OStreamRef[O]) =
    val ctx = TaskContext[I, O]()

    val opSpec = OperatorSpecs.opSpecFromTaskBehavior(ctx, behavior)

    val opref = system.executionContext.execute[I, O](opSpec)

    // FIXME: this is weird, perhaps it is better not to have IStreamRef/OStreamRef.
    val iref = new IStreamRef[I]{
      val opr = opref
      def submit(event: I): Unit = 
        opr.mop.onNext(-1, Event(event))
      def seal(): Unit = 
        opr.mop.onComplete(-1)
      def fuse(): Unit = 
        opr.mop.onNext(-1, Atom())
    }

    val oref = new OStreamRef[O]{
      val opr = opref
      def subscribe(subscriber: IStreamRef[O]): Unit = opr.subscibe(subscriber.opr)
    }

    ctx.opr = opref
    ctx.mainiref = iref
    ctx.mainoref = oref

    (iref, oref)
