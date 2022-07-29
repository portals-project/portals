package portals

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.test.*

@RunWith(classOf[JUnit4])
class RegistryBuilderTest:

  @Test
  def testRegistrySequencer(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val system = Systems.syncLocal()

    ////////////////////////////////////////////////////////////////////////////
    // App 1
    ////////////////////////////////////////////////////////////////////////////
    {
      val builder = ApplicationBuilders
        .application("app1")

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
      val builder = ApplicationBuilders
        .application("app2")

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

    system.stepAll()

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
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val system = Systems.syncLocal()

    ////////////////////////////////////////////////////////////////////////////
    // App 1
    ////////////////////////////////////////////////////////////////////////////
    {
      val builder = ApplicationBuilders
        .application("app1")

      val generator = builder.generators.fromRange(0, 100, 5)

      val _ = builder
        .workflows[Int, Int]("workflow")
        .source(generator.stream)
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
      val builder = ApplicationBuilders
        .application("app2")

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

    ////////////////////////////////////////////////////////////////////////////
    // Execute
    ////////////////////////////////////////////////////////////////////////////

    system.stepAll()

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
