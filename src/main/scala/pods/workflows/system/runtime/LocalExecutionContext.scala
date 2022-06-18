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
    executed.foreach(_.seal())
end LocalExecutionContext // class


object LocalExecutionContext:
  private[this] def translateOpSpecToMultiOp[T, U](opSpec: OperatorSpec[T, U]): MultiOperatorWithAtom[T, U] = 
    new MultiOperatorWithAtom[T, U]{ self =>

      type I = WrappedEvents[T]
      type O = WrappedEvents[U]

      val lock = new ReentrantLock()

      given opctx: OperatorCtx[T, U] =  new OperatorCtx{
        def submit(item: U): Unit = self.submit(Event(item))
        def fuse(): Unit = self.submit(Atom())
        def seal(): Unit = self.seal() 
      }

      var subscribers = List.empty[Subscriber[I]]
      var publishers = List.empty[SubmissionPublisher[O]]

      var _nextId = 0
      def nextId(): Int = 
        _nextId = _nextId + 1
        _nextId

      def onNext(subscriptionId: Int, item: I): Unit = 
        lock.lock()
        item match
          case Event(item) => opSpec.onNext(subscriptionId, item)
          case Atom() => opSpec.onAtomComplete(subscriptionId)
        lock.unlock()

      def onComplete(subscriptionId: Int): Unit = 
        lock.lock()
        opSpec.onComplete(subscriptionId)
        lock.unlock()

      def onError(subscriptionId: Int, error: Throwable): Unit = 
        lock.lock()
        opSpec.onError(subscriptionId, error)
        lock.unlock()

      def onSubscribe(subscriptionId: Int, subscription: Subscription): Unit = 
        lock.lock()
        opSpec.onSubscribe(subscriptionId, subscription)
        lock.unlock()

      def subscribe(msubscriber: MultiSubscriber[O]): Unit = 
        lock.lock()
        val publisher = this.freshPublisher()
        val subscriber = msubscriber.freshSubscriber()
        publishers = publisher :: publishers
        publisher.subscribe(subscriber)
        lock.unlock()
        
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
        lock.lock()
        this.publishers.foreach(_.submit(item))
        lock.unlock()

      def seal(): Unit = 
        // wait for system to reach steady state, else force shutdown
        lock.lock()
        
        import scala.concurrent.duration._
          
        val until = 500.milliseconds.fromNow
        var break = false

        while(until.hasTimeLeft && !break) {
          if this.publishers.forall{ publisher =>
            publisher.estimateMaximumLag() != 0
          } then
            break = true
            this.publishers.foreach{ _.close() }
        }

        lock.unlock()
    }