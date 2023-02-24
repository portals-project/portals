package portals.test

import scala.collection.mutable
import scala.collection.mutable.Queue
import scala.util.Try

import org.junit.Assert._

import portals.*

object TestUtils:
  def executeTask[T, U](
      task: GenericTask[T, U, _, _],
      testData: List[List[T]],
      testDataKeys: List[List[Key[Long]]] = List.empty,
  ): Tester[U] =
    val tester = Tester[U]()
    val builder = ApplicationBuilders.application("app")
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
      testDataKeys: List[List[Key[Long]]] = List.empty,
  ): Tester[U] =
    val tester = Tester[U]()
    val builder = ApplicationBuilders.application("app")

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

    private val queue: Queue[WrappedEvent[T]] = Queue[WrappedEvent[T]]()

    def enqueueEvent(event: T): Unit = queue.enqueue(Event(event))
    def enqueueAtom(): Unit = queue.enqueue(Atom)
    def enqueueSeal(): Unit = queue.enqueue(Seal)
    def enqueueError(t: Throwable): Unit = queue.enqueue(Error(t))

    val task = new ExtensibleTask[T, T, Nothing, Nothing] {
      override def onNext(using ctx: TaskContextImpl[T, T, Nothing, Nothing])(t: T): Unit =
        queue.enqueue(Event(t))
        ctx.emit(t)
      override def onError(using ctx: TaskContextImpl[T, T, Nothing, Nothing])(t: Throwable): Unit =
        queue.enqueue(Error(t))
      override def onComplete(using ctx: TaskContextImpl[T, T, Nothing, Nothing]): Unit =
        queue.enqueue(Seal)
      override def onAtomComplete(using ctx: TaskContextImpl[T, T, Nothing, Nothing]): Unit =
        queue.enqueue(Atom)
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

    def receiveSealAssert(): this.type =
      assertEquals(Some(Seal), Try(queue.dequeue()).toOption)
      this

    def isEmptyAssert(): this.type = { assertEquals(true, isEmpty()); this }

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
      // while !future.isCompleted do ()
      Await.result(future, atMost)

    def task[T](p: T => Boolean) = TaskBuilder.map[T, T] { x => { if p(x) then complete(true); x } }

    // def taskOpt[T](p: T => Option[Boolean]) = TaskBuilder.map[T, T] { x => { p(x) match { case Some(b) => complete(b); }; x } }

    def workflow[T](stream: AtomicStreamRef[T], builder: ApplicationBuilder)(p: T => Boolean): Workflow[T, T] =
      builder
        .workflows[T, T]("completer")
        .source[T](stream)
        .task(task(p(_)))
        .sink[T]()
        .freeze()

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
