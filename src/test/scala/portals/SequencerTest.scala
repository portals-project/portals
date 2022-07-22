package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

import TestUtils.*

@RunWith(classOf[JUnit4])
class SequencerTest:

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
    val iter1 = (0 until 128).map { x => if x % 5 == 0 then Atom else Event(x) }.concat(Iterator(Atom, Seal)).iterator
    val generator1 = builder.generators
      .fromFunction[Int]("generator1", iter1.next, () => iter1.hasNext)

    val iter2 = (128 until 256).map { x => if x % 5 == 0 then Atom else Event(x) }.concat(Iterator(Atom, Seal)).iterator
    val generator2 = builder.generators
      .fromFunction[Int]("generator2", iter2.next, () => iter2.hasNext)

    // consumers
    val consumingWorkflow = builder
      .workflows[Int, Int]("consumingWorkflow")
      .source(sequencer.stream)
      .task(tester.task)
      .sink()
      .freeze()

    // connect producers to sequencer
    val connection1 = builder.connections.connect("connection1", generator1.stream, sequencer)
    val connection2 = builder.connections.connect("connection2", generator2.stream, sequencer)

    val app = builder.build()

    ASTPrinter.println(app)

    val system = Systems.syncLocal()

    system.launch(app)

    system.stepAll()

    system.shutdown()

    val testData = (0 until 256).flatMap { x => if x % 5 == 0 then List.empty else List(x) }.toList
    assertEquals(tester.receiveAll().sorted, testData)
