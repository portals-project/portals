package portals.test

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.mutable
import scala.collection.mutable.Queue
import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.junit.Assert._

import portals.api.builder.ApplicationBuilder
import portals.api.builder.FlowBuilder
import portals.api.builder.TaskBuilder
import portals.application.task.ExtensibleTask
import portals.application.task.GenericTask
import portals.application.task.PerTaskState
import portals.application.task.TaskContextImpl
import portals.application.AtomicStreamRef
import portals.application.Workflow
import portals.system.Systems
import portals.util.Key

object TestUtils:
  def executeTask[T, U](
      task: GenericTask[T, U, _, _],
      testData: List[List[T]],
      testDataKeys: List[List[Key]] = List.empty,
  ): Tester[U] =
    val tester = Tester[U]()
    val builder = ApplicationBuilder("app")
    val generator =
      if testDataKeys.isEmpty then builder.generators.fromListOfLists(testData)
      else builder.generators.fromListOfLists(testData, testDataKeys)
    val workflow = builder
      .workflows[T, U]("workflow")
      .source(generator.stream)
      .task(task)
      .task(tester.task)
      .sink()
      .freeze()
    val app = builder.build()
    val system = Systems.test()
    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()
    tester

  def executeWorkflow[T, U](
      flows: FlowBuilder[T, U, T, T] => FlowBuilder[T, U, U, U],
      testData: List[List[T]],
      testDataKeys: List[List[Key]] = List.empty,
  ): Tester[U] =
    val tester = Tester[U]()
    val builder = ApplicationBuilder("app")

    val generator =
      if testDataKeys.isEmpty then builder.generators.fromListOfLists(testData)
      else builder.generators.fromListOfLists(testData, testDataKeys)

    val workflow = builder
      .workflows[T, U]("workflow")

    flows(
      workflow
        .source(generator.stream)
    )
      .task(tester.task)
      .sink()
      .freeze()

    val app = builder.build()

    val system = Systems.test()

    system.launch(app)
    system.stepUntilComplete()
    system.shutdown()

    tester

  // for building a flowbuilding factory
  def flowBuilder[T, U](
      flows: FlowBuilder[T, U, T, T] => FlowBuilder[T, U, U, U]
  ): FlowBuilder[T, U, T, T] => FlowBuilder[T, U, U, U] =
    flows

  object Tester:
    sealed trait WrappedEvent[+T]
    case class Event[T](event: T) extends WrappedEvent[T]
    case object Atom extends WrappedEvent[Nothing]
    case object Seal extends WrappedEvent[Nothing]
    case class Error(t: Throwable) extends WrappedEvent[Nothing]

  // only for synchronous testing
  class Tester[T]:
    import Tester.*

    private var queue: ConcurrentLinkedQueue[WrappedEvent[T]] = ConcurrentLinkedQueue[WrappedEvent[T]]()

    def enqueueEvent(event: T): Unit = queue.add(Event(event))
    def enqueueAtom(): Unit = queue.add(Atom)
    def enqueueSeal(): Unit = queue.add(Seal)
    def enqueueError(t: Throwable): Unit = queue.add(Error(t))

    val task = new ExtensibleTask[T, T, Nothing, Nothing] {
      override def onNext(using ctx: TaskContextImpl[T, T, Nothing, Nothing])(t: T): Unit =
        queue.add(Event(t))
        ctx.emit(t)
      override def onError(using ctx: TaskContextImpl[T, T, Nothing, Nothing])(t: Throwable): Unit =
        queue.add(Error(t))
      override def onComplete(using ctx: TaskContextImpl[T, T, Nothing, Nothing]): Unit =
        queue.add(Seal)
      override def onAtomComplete(using ctx: TaskContextImpl[T, T, Nothing, Nothing]): Unit =
        queue.add(Atom)
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
        override def hasNext: Boolean = !queue.isEmpty()
        override def next(): Option[WrappedEvent[T]] =
          Try(queue.poll()).toOption
      }.flatMap(x => x)

    def receive(): Option[T] =
      eventIterator(dequeueingIter).nextOption()

    def receiveAssert(event: T): this.type =
      assertEquals(Some(event), receive())
      this

    def receiveAtomAssert(): this.type =
      assertEquals(Some(Atom), Try(queue.poll()).toOption)
      this

    def receiveSealAssert(): this.type =
      assertEquals(Some(Seal), Try(queue.poll()).toOption)
      this

    def isEmptyAssert(): this.type = { assertEquals(true, isEmpty()); this }

    // receive the remaining events of the atom
    def receiveAtom(): Option[Seq[T]] =
      atomIterator(dequeueingIter).nextOption()

    // does not dequeue the elements
    def receiveAll(): Seq[T] =
      queue.asScala.flatMap { unwrap(_) }.toSeq

    // does not dequeue the elements
    def receiveAllWrapped(): Seq[WrappedEvent[T]] =
      queue.asScala.toSeq

    // does not dequeue the elements
    def receiveAllAtoms(): Seq[Seq[T]] =
      atomIterator(queue.asScala.iterator).toSeq

    def isEmpty(): Boolean =
      queue.isEmpty

    def contains(el: T): Boolean =
      queue.contains(Event(el))

object AsyncTestUtils:
  import scala.concurrent.duration.*
  import scala.concurrent.Await
  import scala.concurrent.Promise
  private val atMost = 5.seconds

  class CompletionWatcher():
    private val promise = Promise[Boolean]()
    private val future = promise.future

    def complete(value: Boolean = true): Unit =
      if !promise.isCompleted then promise.success(value)

    def waitForCompletion(): Boolean =
      Await.result(future, atMost)

    def task[T](p: T => Boolean) = TaskBuilder.map[T, T] { x => { if p(x) then complete(true); x } }

    def workflow[T](stream: AtomicStreamRef[T], builder: ApplicationBuilder)(p: T => Boolean): Workflow[T, T] =
      builder
        .workflows[T, T]("completer")
        .source[T](stream)
        .task(task(p(_)))
        .sink[T]()
        .freeze()

  class CountWatcher(count: Int):
    private val completionWatcher = new CompletionWatcher()
    private val counter = new AtomicInteger(0)

    def increment(): Unit =
      if counter.incrementAndGet() == count then //
        completionWatcher.complete()

    def waitForCompletion(): Boolean =
      completionWatcher.waitForCompletion()

    def task[T]() =
      TaskBuilder.map[T, T] { x => { this.increment(); x } }

  class Timer():
    private var startTime: Option[Long] = None
    private var stopTime: Option[Long] = None

    def start: Unit =
      startTime = Some(System.nanoTime())

    def stop: Unit =
      stopTime = Some(System.nanoTime())

    private def isDefined: Boolean = startTime.isDefined && stopTime.isDefined

    def statistics: String =
      if !isDefined then ???
      else
        "Time for this run: " + (stopTime.get - startTime.get) + " ns" + "\n"
          + "Time for this run: " + (stopTime.get - startTime.get) / 1000.0 + " us" + "\n"
          + "Time for this run: " + (stopTime.get - startTime.get) / 1000000.0 + " ms" + "\n"
          + "Time for this run: " + (stopTime.get - startTime.get) / 1000000000.0 + " s"

  object Asserter:
    def workflow[T](stream: AtomicStreamRef[T], builder: ApplicationBuilder)(p: T => Boolean): Workflow[T, T] =
      builder
        .workflows[T, T]("asserter")
        .source[T](stream)
        .map { x => { assertTrue(p(x)); x } }
        .sink[T]()
        .freeze()

    def workflowRange(stream: AtomicStreamRef[Int], builder: ApplicationBuilder)(from: Int, to: Int) =
      val range = Range(from, to).iterator
      workflow[Int](stream, builder) { x => x == range.next() }

    def task[T](p: T => Boolean) = TaskBuilder.map[T, T] { x => { assertTrue(p(x)); x } }

    def taskRange(from: Int, to: Int) =
      val range = Range(from, to).iterator
      TaskBuilder.map[Int, Int] { x => { assertTrue(x == range.next()); x } }

    def taskIterator[T](iterator: Iterator[T]) =
      TaskBuilder.map[Int, Int] { x => { assertTrue(x == iterator.next()); x } }

    def taskMonotonic =
      TaskBuilder.init[Int, Int]:
        val last = PerTaskState("last", -1)
        TaskBuilder.map[Int, Int]: x =>
          assertTrue(x > last.get())
          last.set(x)
          x
