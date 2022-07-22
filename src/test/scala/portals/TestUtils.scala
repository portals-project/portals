package portals

import collection.convert.ImplicitConversions.`collection asJava`
import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable.Queue
import scala.util.Try
import org.junit.Assert._

object TestUtils:
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

  class Tester[T]:
    private val queue: Queue[T] = Queue[T]()

    val task = Tasks.map[T, T] { event =>
      queue.enqueue(event)
      event
    }

    def receiveAssert(event: T): this.type =
      assertEquals(Some(event), receive())
      this

    def receive(): Option[T] =
      val res = Try(queue.dequeue()).toOption
      res

    def peek(): Option[T] =
      val res = Try(queue.front).toOption
      res

    def receiveAll(): Seq[T] =
      val res = queue.toSeq
      res

    def isEmpty(): Boolean =
      val res = queue.isEmpty()
      res

    def contains(el: T): Boolean =
      val res = queue.contains(el)
      res
