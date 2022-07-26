package portals

import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import scala.collection.AnyStepper.AnyStepperSpliterator

// Verify cycles between workflows behave as expected.
@RunWith(classOf[JUnit4])
class CycleTest:

  @Ignore
  @Test
  def testExternalCycle(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("application")

    val sequencer = builder.sequencers.random[Int]("sequencer")

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source[Int](sequencer.stream)
      .flatMap[Int] { ctx ?=> x =>
        if (x > 0) List(x - 1)
        else List.empty
      }
      .task(tester.task)
      .sink()
      .freeze()

    val _ = builder.connections.connect("connection1", workflow.stream, sequencer)

    val generator = builder.generators.fromList[Int]("generator", List(8))
    val _ = builder.connections.connect("connection2", generator.stream, sequencer)

    val application = builder.build()

    val system = Systems.syncLocal()

    system.launch(application)

    system.stepAll()
    system.shutdown()

    assertEquals(Some(7), tester.receiveAtom())
    assertEquals(Some(6), tester.receiveAtom())
    assertEquals(Some(5), tester.receiveAtom())
    assertEquals(Some(4), tester.receiveAtom())
    assertEquals(Some(3), tester.receiveAtom())
    assertEquals(Some(2), tester.receiveAtom())
    assertEquals(Some(1), tester.receiveAtom())
    assertEquals(Some(0), tester.receiveAtom())
    assertNotEquals(Some(-1), tester.receiveAtom())
