package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

import TestUtils.*

@RunWith(classOf[JUnit4])
class SequencerTest:

  @Ignore
  @Test
  def randomTest(): Unit =
    import portals.DSL.*
    import Generator.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("app")

    val sequencer = builder.sequencers
      .random[Int]("random")

    // producers
    val generator1 = builder.generators.fromRange("generator1", 0, 128, 5)
    val generator2 = builder.generators.fromRange("generator2", 128, 256, 5)

    // connect producers to sequencer
    val _ = builder.connections.connect("connection1", generator1.stream, sequencer)
    val _ = builder.connections.connect("connection2", generator2.stream, sequencer)

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
    val receivedWrapped = tester.receiveAllWrapped()
    val receivedAtoms = tester.receiveAllAtoms()
    val testData = Iterator.range(0, 256).toList
    val testDataAtoms = testData.grouped(5).toList

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
