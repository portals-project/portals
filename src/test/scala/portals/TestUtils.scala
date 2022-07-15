package portals

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


    class TestPreSubmitCallback[T] extends PreSubmitCallback[T] {
      val lock = ReentrantLock()
      private val queue: Queue[T] = Queue[T]()

      override def preSubmit(t: T): Unit = {
        lock.lock()
        queue.enqueue(t)
        lock.unlock()
      }

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
    }
