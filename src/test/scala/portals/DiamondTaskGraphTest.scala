package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

/** Diamond task graph pattern test
  * We can create a DAG and not just a sequence the following way
  * this creates a diamond shaped workflow
  *         |------> map _ + 1 ---->
  * source -->                      |---> sink
  *         |------> map _ + 2 ---->
  */
@RunWith(classOf[JUnit4])
class DiamondTaskGraphTest:

  @Test
  def testDiamondTaskGraph(): Unit =

    val builder = Builders.application("application")

    val source = builder
      .workflows[Int, Int]("wf")
      .source[Int]()
      .withName("input")

    val fromSource1 = source
      .map(_ + 1)

    val fromSource2 = source
      .map(_ + 2)

    val merged = fromSource1
      .union(fromSource2)
      .withName("merged")

    val sink = merged
      .sink[Int]()
      .withName("output")

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    // create a test environment IRef

    val iref: IStreamRef[Int] = system.registry("wf/input").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/output").resolve()

    val testIRef = TestUtils.TestPreSubmitCallback[Int]()
    oref.setPreSubmitCallback(testIRef)

    iref.submit(1)
    iref.fuse()

    system.stepAll()
    system.shutdown()

    assertTrue(testIRef.contains(2) && testIRef.contains(3))
    assertFalse(testIRef.contains(1))
