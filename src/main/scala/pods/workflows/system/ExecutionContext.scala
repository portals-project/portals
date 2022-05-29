package pods.workflows

import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.Flow.Subscription
// import java.util.concurrent.Flow.Subscriber

private[pods] trait ExecutionContext:
  def execute[I, O](processor: Processor[I, O]): (SubmissionPublisher[O], IStreamRef[I], OStreamRef[O])

  def shutdown(): Unit

private[pods] object ExecutionContext:
  def apply(): ExecutionContext = new ExecutionContextImpl

private[pods] class ExecutionContextImpl extends ExecutionContext:
  def execute[I, O](processor: Processor[I, O]): (SubmissionPublisher[O], IStreamRef[I], OStreamRef[O]) = 
    val executedProcessor = new SubmissionPublisher[O] with Subscriber[I] {
      def onNext(t: I): Unit = processor.onNext(t)
      def onError(t: Throwable): Unit = processor.onError(t)
      def onComplete(): Unit = processor.onComplete()
      def onSubscribe(s: Subscription): Unit = processor.onSubscribe(s)
      def onAtomComplete(id: Long): Unit = ???
    }

    val iref = new IStreamRef[I]{
      private[pods] val subscriber: Subscriber[I] = executedProcessor
      def submit(event: I): Unit = 
        subscriber.onNext(event)
      def seal(): Unit = 
        subscriber.onComplete()
      def fuse(): Unit = ???
    }

    val oref = new OStreamRef[O]{
      private[pods] val publisher: SubmissionPublisher[O] with Subscriber[I] = executedProcessor
      def subscribe(iref: IStreamRef[O]): Unit = 
        publisher.subscribe(iref.subscriber)
    }
    
    (executedProcessor, iref, oref)


  def shutdown(): Unit = ()

// trait ExecutionContext:
//   def execute[I, O](processor: Processor[I, O]): (IStreamRef[I], OStreamRef[O])

// object ExecutionContext:
//   sealed trait Events[T]
//   case class Event[T](event: T) extends Events[T]
//   case class Atom[T](id: Long) extends Events[T]

//   def apply(): ExecutionContext = new ExecutionContextImpl()

// class ExecutionContextImpl extends ExecutionContext:
//   import ExecutionContext.*

//   def execute[I, O](processor: Processor[I, O]): (IStreamRef[I], OStreamRef[O]) =
//     val executedProcessor = new SubmissionPublisher[O] with Subscriber[Events[I]] {
//       private val _lock = new AnyRef
//       def onNext(t: Events[I]): Unit = 
//         t match
//           case Event(event) => processor.onNext(event)
//           case Atom(id) => processor.onAtomComplete(id)
//       def onError(t: Throwable): Unit = processor.onError(t)
//       def onComplete(): Unit = processor.onComplete()
//       def onSubscribe(s: Subscription): Unit = processor.onSubscribe(s)
//     }
    
//     // val iref = new IStreamRef[I]{
//     //   private[pods] val subscriber: SubmissionPublisher[O] with Subscriber[Events[I]] = executedProcessor
//     //   def submit(event: I): Unit = 
//     //     subscriber.onNext(Event[I](event))
//     //   def seal(): Unit = 
//     //     subscriber.close()
//     //   def fuse(): Unit = 
//     //     subscriber.onNext(Atom(0))
//     // }

//     // val oref = new OStreamRef[O]{
//     //   private[pods] val publisher: SubmissionPublisher[O] with Subscriber[Events[I]] = executedProcessor
//     //   def subscribe(subscriber: IStreamRef[O]): Unit = 
//     //     publisher.subscribe(subscriber.subscriber.asInstanceOf)
//     // }

//     // (iref, oref)

