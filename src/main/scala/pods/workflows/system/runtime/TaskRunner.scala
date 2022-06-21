package pods.workflows

import java.util.concurrent.Flow.{Subscription => FSubscription}
import java.util.concurrent.Flow.{Subscriber => FSubscriber}

private[pods] object TaskRunner:
  def run[I, O](behavior: TaskBehavior[I, O])(using system: SystemContext): (IStreamRef[I], OStreamRef[O]) =
    val ctx = TaskContext[I, O]()

    val opSpec = OperatorSpecs.opSpecFromTaskBehavior(ctx, behavior)

    val opref = system.executionContext.execute[I, O](opSpec)

    // FIXME: the way IStreamRef and OStreamRef work is weird. It is currently
    // used both from within the TaskContext, but also from the outside by the 
    // user. This dual use makes it rather confusing as to what exactly it is
    // and what exactly it should be doing. Instead we should separate these 
    // two.
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
