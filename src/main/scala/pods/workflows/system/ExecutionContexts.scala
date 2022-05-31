package pods.workflows

import java.util.concurrent.{SubmissionPublisher => FSubmissionPublisher}
import java.util.concurrent.Flow.{Subscription => FSubscription}
import java.util.concurrent.Flow.{Subscriber => FSubscriber}

object ExecutionContexts:
  def local(): ExecutionContext = new LocalExecutionContext()

  //////////////////////////////////////////////////////////////////////////////
  // Local Execution Context
  //////////////////////////////////////////////////////////////////////////////
  
  private[pods] class LocalExecutionContext extends ExecutionContext:
    /** Wraps events for transport over the streams */
    private[pods] sealed trait WrappedStreamEvents[T]
    private[pods] case class Event[T](event: T) extends WrappedStreamEvents[T]
    private[pods] case class Atom[T](id: Long) extends WrappedStreamEvents[T]

    /** Contains any information about the execution or scheduler at runtime */
    trait SchedulerInfo:
      // Contains any information, the type of information may differ for different schedulers.
      val info: AnyRef
      
    private var executedProcessors = List.empty[FSubmissionPublisher[_]]

    def execute[I, O](processor: Processor[I, O]): (SubmitterProcessor[I, O], IStreamRef[I], OStreamRef[O]) = 

      // 1. execute the processor on the underlying streams processor
      val executedProcessor = new FSubmissionPublisher[WrappedStreamEvents[O]] with FSubscriber[WrappedStreamEvents[I]] {
        def onNext(t: WrappedStreamEvents[I]): Unit = 
          t match
            case Event(event) => 
              processor.onNext(event)
            case Atom(id) => 
              processor.onAtomComplete(id)
        def onError(t: Throwable): Unit = 
          processor.onError(t)
        def onComplete(): Unit = 
          processor.onComplete()
        def onSubscribe(s: FSubscription): Unit = 
          processor.onSubscribe(s)
      }

      // append to executed processors
      executedProcessors = executedProcessor :: executedProcessors

      // 2. wrap the processor in compatible format as a SubmitterProcessor
      val submitterProcessor = new SubmitterProcessor[I, O] with SchedulerInfo {
        // store the executed processor in the info field
        val info: AnyRef = executedProcessor
        def onNext(t: I): Unit = 
          executedProcessor.onNext(Event(t))
        def onError(t: Throwable): Unit = 
          executedProcessor.onError(t)
        def onComplete(): Unit = 
          executedProcessor.onComplete()
        def onAtomComplete(id: Long): Unit = 
          executedProcessor.onNext(Atom(id))
        def onSubscribe(s: FSubscription): Unit = 
          executedProcessor.onSubscribe(s)
        def fuse(): Unit = 
          executedProcessor.submit(Atom(0))
        def seal(): Unit = 
          executedProcessor.close()
        def submit(event: O): Unit = 
          executedProcessor.submit(Event(event))
        def subscribe(s: FSubscriber[_ >:O]): Unit = 
          executedProcessor.subscribe(
            // get the stored executed processor from the info field
            s
              .asInstanceOf[SchedulerInfo]
              .info.asInstanceOf[FSubscriber[WrappedStreamEvents[O]]]
          )
      }

      // 3. create the IStreamRef
      val iref = new IStreamRef[I]{
        private[pods] val subscriber: Subscriber[I] = submitterProcessor
        def submit(event: I): Unit = 
          subscriber.onNext(event)
        def seal(): Unit = 
          subscriber.onComplete()
        def fuse(): Unit = 
          subscriber.onAtomComplete(0)
      }

      // 4. create OStreamRef
      val oref = new OStreamRef[O]{
        private[pods] val publisher: Publisher[O] = submitterProcessor
        def subscribe(iref: IStreamRef[O]): Unit = 
          publisher.subscribe(iref.subscriber)
      }
      
      // 5. return
      (submitterProcessor, iref, oref)

    def shutdown(): Unit = 
      // wait for system to reach steady state, else force shutdown
      
      import scala.concurrent.duration._
        
      val until = 500.milliseconds.fromNow
      var break = false

      while(until.hasTimeLeft && !break) {
        if executedProcessors.forall{ sp =>
          sp.estimateMaximumLag() != 0
        } then
          break = true
          executedProcessors.foreach{ _.close() }
          return // return
      }

      executedProcessors.foreach(
        _.closeExceptionally(new Exception("LocalExecutionContext shutdown"))
      )
      return