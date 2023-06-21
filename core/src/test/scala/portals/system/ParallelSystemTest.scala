package portals.system

import scala.concurrent.Promise

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.api.builder.ApplicationBuilder
import portals.api.builder.TaskBuilder
import portals.api.dsl.DSL
import portals.application.task.PerTaskState
import portals.application.AtomicStreamRef
import portals.application.Workflow
import portals.system.Systems
import portals.test.*
import portals.test.AsyncTestUtils
import portals.test.AsyncTestUtils.Asserter

@RunWith(classOf[JUnit4])
class ParallelSystemTest:
  @Test
  def lotsOfEventsTest(): Unit =
    import portals.api.dsl.DSL.*

    val to = 1024 * 1024

    val counter = AsyncTestUtils.CountWatcher(to)

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, to, 1024 * 16)

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      .key(x => x)
      // // assert that the events are monotonically increasing
      .task(Asserter.taskMonotonic)
      // assert that `to` events are counted
      .task(counter.task())
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.parallel(8)

    system.launch(application)

    counter.waitForCompletion()

    system.shutdown()

  @Test
  def largeAtomsTest(): Unit =
    import portals.api.dsl.DSL.*

    val to = 1024 * 1024

    val counter = AsyncTestUtils.CountWatcher(to)

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, to, to / 4)

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source(generator.stream)
      // assert that the events are monotonically increasing
      .task(Asserter.taskMonotonic)
      // assert that `to` events are counted
      .task(counter.task())
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.parallel(8)

    system.launch(application)

    counter.waitForCompletion()

    system.shutdown()

  @Test
  @Ignore // currently there are issues with longer chains of workflows
  def chainOfWorkflowsTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val builder = ApplicationBuilder("app")

    val from = 0
    val to = 4
    val step = 1
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

    // completer
    completer.workflow[Int](prev.stream, builder) { x => x == to + chainlength - 1 }

    val application = builder.build()

    val system = Systems.parallel(2)

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()

  @Test
  def chainOfTasksTest(): Unit =
    import portals.api.dsl.DSL.*

    val completer = AsyncTestUtils.CompletionWatcher()

    val builder = ApplicationBuilder("app")

    val from = 0
    val to = 1024 * 16
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
      .task((Asserter.taskMonotonic)) // assert
      .task(completer.task(x => x == to + chainlength - 1)) // terminate
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.parallel(8)

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
      .task(completer.task(x => x == to - 1)) // terminate
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.parallel(8)

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()

  @Test
  @Ignore // currently there are issues
  def manyGeneratorsTest(): Unit =
    import portals.api.dsl.DSL.*

    val counter = AsyncTestUtils.CountWatcher(1024)

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
      .task(counter.task())
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.parallel(8)

    system.launch(application)

    counter.waitForCompletion()

    system.shutdown()

  @Test
  @Ignore // currently there are issues with longer chains
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

    val system = Systems.parallel(8)

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()
