package pods.workflows

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.Flow.Subscription
import java.util.concurrent.Flow.Publisher
import collection.convert.ImplicitConversions.`collection asJava`

import pods.workflows.*

  object TestUtils:
    
    class TestIStreamRef[T] extends IStreamRef[T]:
      private val queue = new ConcurrentLinkedQueue[T]()

      def receiveAssert(event: T): this.type = 
        assert(event == queue.poll())
        this

      def receive(): Option[T] = 
        Option(queue.poll())

      def peek(): Option[T] =
        Option(queue.peek())

      def receiveAll(): Seq[T] = 
        queue.toArray.asInstanceOf[Array[T]].toSeq

      def isEmpty(): Boolean = 
        queue.isEmpty()

      def contains(el: T): Boolean = 
        queue.contains(el)

      private[pods] val opr: OpRef[T, Nothing] = new OpRef[T, Nothing]{
        val mop = new MultiOperatorWithAtom[T, Nothing]{
          private[pods] def freshSubscriber(): Subscriber[WrappedEvents[T]] = new Subscriber[WrappedEvents[T]]{
            def onSubscribe(s: Subscription): Unit =
              s.request(Long.MaxValue)
            def onNext(t: WrappedEvents[T]): Unit = t match
              case Event(e) => queue.add(e)
              case Atom() => () // probably do something
            def onError(t: Throwable): Unit = ???
            def onComplete(): Unit = ???
          }
          
          // leave unimplemented, these are not used :)
          def onNext(subscriptionId: Int, item: WrappedEvents[T]): Unit = ???
          def onComplete(subscriptionId: Int): Unit = ???
          def onError(subscriptionId: Int, error: Throwable): Unit = ???
          def onSubscribe(subscriptionId: Int, subscription: Subscription): Unit = ???
          def subscribe(msubscriber: MultiSubscriber[WrappedEvents[Nothing]]): Unit = ???
          private[pods] def freshPublisher(): Publisher[WrappedEvents[Nothing]] = ???
          def submit(item: WrappedEvents[Nothing]): Unit = ???
          def seal(): Unit = ???
        }

        // leave unimplemented, these are not used :)
        def submit(item: Nothing): Unit = ???
        def seal(): Unit  = ???
        def subscibe(mref: OpRef[Nothing, _]): Unit = ???
        def fuse(): Unit = ???
      }

      // leave unimplemented, these are not used :)
      private[pods] def submit(event: T): Unit = ???
      private[pods] def fuse(): Unit = ???
      private[pods] def seal(): Unit = ???

    end TestIStreamRef // class



