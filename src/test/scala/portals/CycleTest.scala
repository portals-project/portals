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

  @Test
  def testExternalCycle(): Unit =
    import portals.DSL.*

    val builder = Builders.application("application")

    val src = builder
      .workflows[Int, Int]("wf")
      .source[Int]()
      // .checkExpectedType[Int, Int]()
      .withName("src")
      .flatMap[Int] { ctx ?=> x =>
        if (x > 0) List(x - 1)
        else List.empty
      }
      .sink()
      .withName("loop")

    val application = builder.build()
    val system = Systems.syncLocal()
    system.launch(application)

    val iref: IStreamRef[Int] = system.registry("wf/src").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/loop").resolve()
    oref.subscribe(iref)

    // create a test environment IRef
    val testIRef = TestUtils.TestPreSubmitCallback[Int]()

    // subscribe testIRef to workflow
    oref.setPreSubmitCallback(testIRef)

    iref.submit(8)
    iref.fuse()

    system.stepAll()

    assertTrue(testIRef.contains(7))
    assertTrue(testIRef.contains(6))
    assertTrue(testIRef.contains(5))
    assertTrue(testIRef.contains(4))
    assertTrue(testIRef.contains(3))
    assertTrue(testIRef.contains(2))
    assertTrue(testIRef.contains(1))
    assertTrue(testIRef.contains(0))
    assertFalse(testIRef.contains(-1))

    iref.submit(8)
    iref.fuse()
    system.stepAll()
    system.shutdown()

    assertTrue(testIRef.contains(7))
    assertTrue(testIRef.contains(6))
    assertTrue(testIRef.contains(5))
    assertTrue(testIRef.contains(4))
    assertTrue(testIRef.contains(3))
    assertTrue(testIRef.contains(2))
    assertTrue(testIRef.contains(1))
    assertTrue(testIRef.contains(0))
    assertFalse(testIRef.contains(-1))
