package portals.system.async

import scala.concurrent.Promise

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.*
import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.api.dsl.DSL
import portals.application.task.PerTaskState
import portals.application.AtomicStreamRef
import portals.application.Workflow
import portals.test.*
import portals.test.AsyncTestUtils
import portals.test.AsyncTestUtils.Asserter

@RunWith(classOf[JUnit4])
class AsyncTest:
  @Test
  def lotsOfEventsTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val to = 1024 * 1024

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, to, 128)

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .task(Asserter.taskRange(0, to)) // assert
      .task(completer.task(x => x == to - 1)) // terminate
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.local()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()

  @Test
  def largeAtomsTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val to = 1024 * 1024

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, to, to / 4)

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .task(Asserter.taskRange(0, to)) // assert
      .task(completer.task(x => x == to - 1)) // terminate
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.local()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()

  @Test
  def chainOfWorkflowsTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val builder = ApplicationBuilder("app")

    val from = 0
    val to = 1024 * 8
    val step = 128
    val generator = builder.generators.fromRange(0, to, step)

    val chainlength = 128

    def workflowFactory(name: String, stream: AtomicStreamRef[Int]): Workflow[Int, Int] =
      builder
        .workflows[Int, Int](name)
        .source[Int](stream)
        .map(_ + 1)
        .sink()
        .freeze()

    var prev: Workflow[Int, Int] = workflowFactory("wf0", generator.stream)

    Range(1, chainlength).foreach { i =>
      prev = workflowFactory("wf" + i, prev.stream)
    }

    // asserter
    val asserter = AsyncTestUtils.Asserter.workflowRange(prev.stream, builder)(chainlength, to + chainlength)

    // completer
    completer.workflow[Int](asserter.stream, builder) { x => x == to + chainlength - 1 }

    val application = builder.build()

    val system = Systems.local()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()

  @Test
  def chainOfTasksTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val builder = ApplicationBuilder("app")

    val from = 0
    val to = 1024 * 8
    val step = 128
    val generator = builder.generators.fromRange(from, to, step)

    val chainlength = 128

    var prev = builder
      .workflows[Int, Int]("workflow")
      .source[Int](generator.stream)

    Range(0, chainlength).foreach { i =>
      prev = prev.map { _ + 1 }
    }

    val range = Range(from + chainlength, to + chainlength).iterator
    prev
      .map { x => { assertTrue(x == range.next()); x } } // assert
      .task(completer.task(x => x == to + chainlength - 1)) // terminate
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.local()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()

  @Test
  def taskFanOutInTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val to = 1024

    val fanWidth = 128

    val assertOutput = Range(0, to).flatMap { x => List.fill(fanWidth)(x) }.iterator

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, to, 1)

    val prev = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)

    val fans = Range(0, fanWidth).map { i => prev.map { x => x } }.toList
    val fanIn = fans.head.union(fans.tail)

    fanIn
      .task(Asserter.taskIterator(assertOutput)) // assert
      .task(completer.task(x => x == to - 1)) // terminate
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.local()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()

  @Test
  def manyGeneratorsTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val to = 1024

    val fanWidth = 128

    val builder = ApplicationBuilder("app")

    val sequencer = builder.sequencers.random[Int]()

    Range(0, fanWidth).foreach { i =>
      val generator = builder.generators.fromRange(0, to, 1)
      builder.connections.connect(generator.stream, sequencer)
    }

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(sequencer.stream)
      // completer
      .init[Int] { ctx ?=>
        val state = PerTaskState[Int]("state", 0)
        TaskBuilder.map { x =>
          if x == to - 1 then
            state.set(state.get() + 1)
            if state.get() == fanWidth then completer.complete()
          x
        }
      }
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.local()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()

  @Test
  def manySequencersTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val to = 1024 * 128

    val chainLength = 32

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, to, 128)

    var prev = generator.stream
    Range(0, chainLength).foreach { i =>
      val sequencer = builder.sequencers.random[Int]()
      builder.connections.connect(prev, sequencer)
      prev = sequencer.stream
    }

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(prev)
      .task(completer.task(x => x == to - 1)) // terminate
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.local()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()
