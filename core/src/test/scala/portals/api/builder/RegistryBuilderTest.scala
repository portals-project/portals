package portals.api.builder

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.*
import portals.api.builder.ApplicationBuilder
import portals.api.dsl.DSL
import portals.test.*
import portals.test.TestUtils

@RunWith(classOf[JUnit4])
class RegistryBuilderTest:

  @Test
  def testRegistrySplitter(): Unit =
    import portals.api.dsl.DSL.*

    val tester1 = new TestUtils.Tester[Int]()
    val tester2 = new TestUtils.Tester[Int]()

    val system = Systems.test()

    ////////////////////////////////////////////////////////////////////////////
    // App 1
    ////////////////////////////////////////////////////////////////////////////
    {
      val builder = ApplicationBuilder("app1")

      val generator = builder.generators.fromRange(0, 100, 5)

      val splitter = builder.splitters("splitter").empty[Int](generator.stream)

      val app = builder.build()

      system.launch(app)
    }

    ////////////////////////////////////////////////////////////////////////////
    // App 2
    ////////////////////////////////////////////////////////////////////////////

    {
      val builder = ApplicationBuilder("app2")

      // REGISTRY
      val extSplitter = builder.registry.splitters.get[Int]("/app1/splitters/splitter")

      // split 1
      val split1 = builder.splits("split1").split(extSplitter, _ % 2 == 0)

      // split 2
      val split2 = builder.splits("split2").split(extSplitter, _ % 2 == 1)

      val _ = builder
        .workflows[Int, Int]("workflow1")
        .source(split1)
        .task(tester1.task)
        .sink()
        .freeze()

      val _ = builder
        .workflows[Int, Int]("workflow2")
        .source(split2)
        .task(tester2.task)
        .sink()
        .freeze()

      val app = builder.build()

      system.launch(app)
    }

    ////////////////////////////////////////////////////////////////////////////
    // Execute
    ////////////////////////////////////////////////////////////////////////////

    system.stepUntilComplete()

    system.shutdown()

    ////////////////////////////////////////////////////////////////////////////
    // Assert
    ////////////////////////////////////////////////////////////////////////////

    val testData = List.range(0, 100)
    val testDataAtoms = List.range(0, 100).grouped(5).toList
    val testData1 = testData.filter(_ % 2 == 0)
    val testData2 = testData.filter(_ % 2 == 1)
    val testDataAtoms1 = testDataAtoms.map(_.filter(_ % 2 == 0))
    val testDataAtoms2 = testDataAtoms.map(_.filter(_ % 2 == 1))

    val received1 = tester1.receiveAll()
    val receivedAtoms1 = tester1.receiveAllAtoms().filter(_.nonEmpty)
    val received2 = tester2.receiveAll()
    val receivedAtoms2 = tester2.receiveAllAtoms().filter(_.nonEmpty)

    // 1. Received the correct data
    assertEquals(testData1, received1)
    assertEquals(testData2, received2)

    // // 2. Received the correct atoms
    assertEquals(testDataAtoms1, receivedAtoms1)
    assertEquals(testDataAtoms2, receivedAtoms2)

  @Test
  def testRegistrySequencer(): Unit =
    import portals.api.dsl.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val system = Systems.test()

    ////////////////////////////////////////////////////////////////////////////
    // App 1
    ////////////////////////////////////////////////////////////////////////////
    {
      val builder = ApplicationBuilder("app1")

      val sequencer = builder.sequencers("sequencer").random[Int]()

      val _ = builder
        .workflows[Int, Int]("workflow")
        .source(sequencer.stream)
        .task(tester.task)
        .sink()
        .freeze()

      val app = builder.build()

      // ASTPrinter.println(app)

      system.launch(app)
    }

    ////////////////////////////////////////////////////////////////////////////
    // App 2
    ////////////////////////////////////////////////////////////////////////////

    {
      val builder = ApplicationBuilder("app2")

      val generator = builder.generators.fromRange(0, 100, 5)

      // REGISTRY
      val extSequencer = builder.registry.sequencers.get[Int]("/app1/sequencers/sequencer")

      builder.connections.connect(generator.stream, extSequencer)

      val app = builder.build()

      // ASTPrinter.println(app)

      system.launch(app)
    }

    ////////////////////////////////////////////////////////////////////////////
    // Execute
    ////////////////////////////////////////////////////////////////////////////

    system.stepUntilComplete()

    system.shutdown()

    ////////////////////////////////////////////////////////////////////////////
    // Assert
    ////////////////////////////////////////////////////////////////////////////

    val received = tester.receiveAll()
    val receivedWrapped = tester.receiveAllWrapped()
    val receivedAtoms = tester.receiveAllAtoms()
    val testData = List.range(0, 100)
    val testDataAtoms = testData.grouped(5).toList

    // 1. all events have been received
    assertEquals(testData, received)

    // 2. the atoms are in the same order as they were generated
    testDataAtoms.zip(receivedAtoms).foreach { case (expected, actual) =>
      assertEquals(expected, actual.toList)
    }

  @Test
  def testRegistryStream(): Unit =
    import portals.api.dsl.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val system = Systems.test()

    ////////////////////////////////////////////////////////////////////////////
    // App 1
    ////////////////////////////////////////////////////////////////////////////
    {
      val builder = ApplicationBuilder("app1")

      val sequencer = builder.sequencers("sequencer").random[Int]()

      val _ = builder
        .workflows[Int, Int]("workflow")
        .source(sequencer.stream)
        .sink()
        .freeze()

      val app = builder.build()

      // ASTPrinter.println(app)

      system.launch(app)
    }

    ////////////////////////////////////////////////////////////////////////////
    // App 2
    ////////////////////////////////////////////////////////////////////////////

    {
      val builder = ApplicationBuilder("app2")

      // REGISTRY
      val extStream = builder.registry.streams.get[Int]("/app1/workflows/workflow/stream")

      val _ = builder
        .workflows[Int, Int]("workflow")
        .source(extStream)
        .task(tester.task)
        .sink()
        .freeze()

      val app = builder.build()

      // ASTPrinter.println(app)

      system.launch(app)
    }

    {
      val builder = ApplicationBuilder("data")

      val generator = builder.generators.fromRange(0, 100, 5)

      // REGISTRY
      val extSequencer = builder.registry.sequencers.get[Int]("/app1/sequencers/sequencer")

      val connection = builder.connections.connect(generator.stream, extSequencer)

      val app = builder.build()

      system.launch(app)
    }

    ////////////////////////////////////////////////////////////////////////////
    // Execute
    ////////////////////////////////////////////////////////////////////////////

    system.stepUntilComplete()

    system.shutdown()

    ////////////////////////////////////////////////////////////////////////////
    // Assert
    ////////////////////////////////////////////////////////////////////////////

    val received = tester.receiveAll()
    val receivedWrapped = tester.receiveAllWrapped()
    val receivedAtoms = tester.receiveAllAtoms()
    val testData = List.range(0, 100)
    val testDataAtoms = testData.grouped(5).toList

    // 1. all events have been received
    assertEquals(testData, received)

    // 2. the atoms are in the same order as they were generated
    testDataAtoms.zip(receivedAtoms).foreach { case (expected, actual) =>
      assertEquals(expected, actual.toList)
    }
