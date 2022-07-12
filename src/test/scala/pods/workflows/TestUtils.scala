package pods.workflows

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.Flow.Subscription
import java.util.concurrent.Flow.Publisher
import collection.convert.ImplicitConversions.`collection asJava`
import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable.Queue

  object TestUtils:

    def forwardingWorkflow[T, U](name: String)(using system: SystemContext): (IStreamRef[T], OStreamRef[U], Workflow) =
      val builder = Workflows
        .builder()
        .withName(name)
      
      val flow = builder
        .source[T]()
        .withName("source")
        .sink()
        .withName("sink")

      val wf = builder.build()
      
      system.launch(wf)

      val iref = system.registry[T](name + "/source").resolve() 
      val oref = system.registry.orefs[U](name + "/sink").resolve() 
      (iref, oref, wf)


    class TestIStreamRef[T] extends IStreamRef[T]:
      val lock = ReentrantLock()

      // private val queue = new ConcurrentLinkedQueue[T]()
      private val queue: Queue[T] = Queue[T]()

      def receiveAssert(event: T): this.type = 
        lock.lock()
        assert(event == queue.dequeue())
        lock.unlock()
        this

      def receive(): Option[T] = 
        lock.lock()
        val res = Option(queue.dequeue())
        lock.unlock()
        res

      def peek(): Option[T] =
        lock.lock()
        val res = Option(queue.front)
        lock.unlock()
        res

      def receiveAll(): Seq[T] = 
        lock.lock()
        // queue.toArray.asInstanceOf[Array[T]].toSeq
        val res = queue.toSeq
        lock.unlock()
        res

      def isEmpty(): Boolean = 
        lock.lock()
        val res = queue.isEmpty()
        lock.unlock()
        res

      def contains(el: T): Boolean = 
        lock.lock()
        val res = queue.contains(el)
        lock.unlock()
        res

      private[pods] val opr: OpRef[T, Nothing] = new OpRef[T, Nothing]{
        val mop = new MultiOperatorWithAtom[T, Nothing]{
          private[pods] def freshSubscriber(): Subscriber[WrappedEvents[T]] = new Subscriber[WrappedEvents[T]]{
            def onSubscribe(s: Subscription): Unit =
              s.request(Long.MaxValue)
            def onNext(t: WrappedEvents[T]): Unit = 
              lock.lock()
              val res = t match
                case Event(e) => queue.enqueue(e)
                case Atom() => () // probably do something
              lock.unlock()
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



