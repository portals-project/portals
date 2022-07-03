package pods.workflows

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.Flow.Subscription

private[pods] class LocalExecutionContext extends ExecutionContext:
  private var executed = List.empty[OpRef[_, _]]

  def execute[T, U](opSpec: OperatorSpec[T, U]): OpRef[T, U] = 
    val multiOp = LocalExecutionContext.translateOpSpecToMultiOp(opSpec)
    val opRef = OpRef.fromMultiOperator(multiOp)
    executed = opRef :: executed
    opRef

  def shutdown(): Unit = 
    Thread.sleep(500)
    executed.foreach(_.seal())
end LocalExecutionContext // class


object LocalExecutionContext:
  private[this] def translateOpSpecToMultiOp[T, U](opSpec: OperatorSpec[T, U]): MultiOperatorWithAtom[T, U] = 
    new MultiOperatorWithAtom[T, U]{ self =>
      val lock = new ReentrantLock()

      type I = WrappedEvents[T]
      type O = WrappedEvents[U]

      given opctx: OperatorCtx[T, U] =  new OperatorCtx{
        def submit(item: U): Unit = 
          self.submit(Event(item))
        def fuse(): Unit = 
          self.submit(Atom())
        def seal(): Unit = 
          self.seal() 
      }

      @volatile var subscribers = List.empty[Subscriber[I]]
      @volatile var publishers = List.empty[SubmissionPublisher[O]]

      @volatile var _nextId = 0
      def nextId(): Int = 
        _nextId = _nextId + 1
        _nextId

      def onNext(subscriptionId: Int, item: I): Unit = 
        item match
          case Event(item) => opSpec.onNext(subscriptionId, item)
          case Atom() => opSpec.onAtomComplete(subscriptionId)

      def onComplete(subscriptionId: Int): Unit = 
        opSpec.onComplete(subscriptionId)

      def onError(subscriptionId: Int, error: Throwable): Unit = 
        opSpec.onError(subscriptionId, error)

      def onSubscribe(subscriptionId: Int, subscription: Subscription): Unit = 
        opSpec.onSubscribe(subscriptionId, subscription)

      def subscribe(msubscriber: MultiSubscriber[O]): Unit = 
        val publisher = this.freshPublisher()
        val subscriber = msubscriber.freshSubscriber()
        publishers = publisher :: publishers
        publisher.subscribe(subscriber)
        
      def freshSubscriber(): Subscriber[I] = 
        new Subscriber[I]{
          val id = nextId()
          def onNext(item: I): Unit = self.onNext(id, item)
          def onComplete(): Unit = self.onComplete(id)
          def onError(error: Throwable): Unit = self.onError(id, error)
          def onSubscribe(subscription: Subscription): Unit = self.onSubscribe(id, subscription)
        }

      def freshPublisher(): SubmissionPublisher[O] = 
        new SubmissionPublisher[O]{
          val id = nextId()
        }

      def submit(item: O): Unit = 
        this.publishers.foreach(_.submit(item))

      def seal(): Unit = 
        this.publishers.foreach{ _.close() }
        // // wait for system to reach steady state, else force shutdown
        // lock.lock()
        
        // import scala.concurrent.duration._
          
        // val until = 500.milliseconds.fromNow
        // var break = false

        // while(until.hasTimeLeft && !break) {
        //   if this.publishers.forall{ publisher =>
        //     publisher.estimateMaximumLag() != 0
        //   } then
        //     break = true
        //     this.publishers.foreach{ _.close() }
        // }

        // lock.unlock()
    }