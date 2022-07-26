package portals

import collection.convert.ImplicitConversions.`collection asJava`
import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable.Queue
import scala.util.Try
import org.junit.Assert._
import scala.util.{Success, Failure}

object TestUtils:
  // // TODO: remove
  // class TestPreSubmitCallback[T] extends PreSubmitCallback[T] {
  //   val lock = ReentrantLock()
  //   private val queue: Queue[T] = Queue[T]()

  //   override def preSubmit(t: T): Unit = {
  //     lock.lock()
  //     queue.enqueue(t)
  //     lock.unlock()
  //   }

  //   def receiveAssert(event: T): this.type =
  //     lock.lock()
  //     assert(event == queue.dequeue())
  //     lock.unlock()
  //     this

  //   def receive(): Option[T] =
  //     lock.lock()
  //     val res = Option(queue.dequeue())
  //     lock.unlock()
  //     res

  //   def peek(): Option[T] =
  //     lock.lock()
  //     val res = Option(queue.front)
  //     lock.unlock()
  //     res

  //   def receiveAll(): Seq[T] =
  //     lock.lock()
  //     // queue.toArray.asInstanceOf[Array[T]].toSeq
  //     val res = queue.toSeq
  //     lock.unlock()
  //     res

  //   def isEmpty(): Boolean =
  //     lock.lock()
  //     val res = queue.isEmpty()
  //     lock.unlock()
  //     res

  //   def contains(el: T): Boolean =
  //     lock.lock()
  //     val res = queue.contains(el)
  //     lock.unlock()
  //     res
  // }

  // only for synchronous testing
  class Tester[T]:
    sealed trait WrappedEvent[+T]
    case class Event[T](event: T) extends WrappedEvent[T]
    case object Atom extends WrappedEvent[Nothing]
    case object Seal extends WrappedEvent[Nothing]
    case class Error(t: Throwable) extends WrappedEvent[Nothing]

    private val queue: Queue[WrappedEvent[T]] = Queue[WrappedEvent[T]]()

    val task = new Task[T, T] {
      override def onNext(ctx: TaskContext[T, T])(t: T): Task[T, T] =
        queue.enqueue(Event(t))
        ctx.emit(t)
        Tasks.same
      override def onError(ctx: TaskContext[T, T])(t: Throwable): Task[T, T] =
        queue.enqueue(Error(t))
        Tasks.same
      override def onComplete(ctx: TaskContext[T, T]): Task[T, T] =
        queue.enqueue(Seal)
        Tasks.same
      override def onAtomComplete(ctx: TaskContext[T, T]): Task[T, T] =
        queue.enqueue(Atom)
        ctx.fuse()
        Tasks.same
    }

    def workflow(stream: AtomicStreamRef[T], builder: ApplicationBuilder): Workflow[T, T] =
      builder
        .workflows[T, T]("tester")
        .source[T](stream)
        .task(this.task)
        .sink[T]()
        .freeze()

    private def unwrap(wrapped: WrappedEvent[T]): Option[T] = wrapped match
      case Event(event) => Some(event)
      case Atom => None
      case Seal => None
      case Error(t: Throwable) => None

    private def eventIterator(iter: Iterator[WrappedEvent[T]]): Iterator[T] =
      iter.flatMap(unwrap)

    private def atomIterator(iter: Iterator[WrappedEvent[T]]): Iterator[Seq[T]] =
      new Iterator[Seq[T]] {
        override def hasNext: Boolean = iter.hasNext
        override def next(): Seq[T] =
          iter
            // consume all events/errors/seals until we find atom
            .takeWhile { case Atom => false; case _ => true }
            // extract all events
            .flatMap(unwrap)
            .toSeq
      }

    private def dequeueingIter: Iterator[WrappedEvent[T]] =
      new Iterator[Option[WrappedEvent[T]]] {
        override def hasNext: Boolean = queue.nonEmpty
        override def next(): Option[WrappedEvent[T]] =
          Try(queue.dequeue()).toOption
      }.flatMap(x => x)

    def receive(): Option[T] =
      eventIterator(dequeueingIter).nextOption()

    def receiveAssert(event: T): this.type =
      assertEquals(Some(event), receive())
      this

    // receive the remaining events of the atom
    def receiveAtom(): Option[Seq[T]] =
      atomIterator(dequeueingIter).nextOption()

    // does not dequeue the elements
    def receiveAll(): Seq[T] =
      queue.flatMap { unwrap(_) }.toSeq

    // does not dequeue the elements
    def receiveAllWrapped(): Seq[WrappedEvent[T]] =
      queue.toSeq

    // does not dequeue the elements
    def receiveAllAtoms(): Seq[Seq[T]] =
      atomIterator(queue.iterator).toSeq

    def isEmpty(): Boolean =
      queue.isEmpty()

    def contains(el: T): Boolean =
      queue.contains(Event(el))
