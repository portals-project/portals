package portals

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test

import portals.test.*

@RunWith(classOf[JUnit4])
class SequencerTest:

  @Test
  def randomTest(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("app")

    val sequencer = builder.sequencers
      .random[Int]()

    // producers
    val generator1 = builder.generators.fromRange(0, 128, 5)
    val generator2 = builder.generators.fromRange(128, 256, 5)

    // connect producers to sequencer
    val _ = builder.connections.connect(generator1.stream, sequencer)
    val _ = builder.connections.connect(generator2.stream, sequencer)

    // consumers
    val _ = builder
      .workflows[Int, Int]("consumingWorkflow")
      .source(sequencer.stream)
      .task(tester.task)
      .sink()
      .freeze()

    val app = builder.build()

    // ASTPrinter.println(app)

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()

    val received = tester.receiveAll()
    val receivedAtoms = tester.receiveAllAtoms()
    val testData = Iterator.range(0, 256).toList
    val testDataAtoms = Iterator.range(0, 128).grouped(5).toList ++ Iterator.range(128, 256).grouped(5).toList

    // 1. all events have been produced and consumed
    assertEquals(received.sorted, testData)

    // 2. the received events are in the same order as the events that were produced
    val testData21 = testData.filter(_ < 128)
    val testData22 = testData.filter(_ >= 128)
    assertEquals(received.filter(_ < 128), testData21)
    assertEquals(received.filter(_ >= 128), testData22)

    // 3. the atoms are not fused / jumbled by the sequencer
    testDataAtoms.foreach { atom =>
      assertEquals(true, receivedAtoms.contains(atom))
    }
