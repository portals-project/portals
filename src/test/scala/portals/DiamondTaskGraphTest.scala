package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

/** Diamond task graph pattern test
  */
@RunWith(classOf[JUnit4])
class DiamondTaskGraphTest:

  @Ignore
  @Test
  def testDiamondTaskGraph(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("application")

    val generator = builder.generators.fromList("generator", List(1))

    val wfb = builder
      .workflows[Int, Int]("wf")

    val source = wfb
      .source[Int](generator.stream)

    val fromSource1 = source
      .map(_ + 1)

    val fromSource2 = source
      .map(_ + 2)

    val fromSource3 = source
      .map(_ + 3)

    val merged = fromSource1
      .union(fromSource2)
      .union(fromSource3)

    val sink = merged
      .task(tester.task)
      // .logger()
      .sink[Int]()

    val _ = wfb.freeze()

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    system.stepAll()
    system.shutdown()

    // 1. The output does not contain 1
    assertFalse(tester.contains(1))

    // 2. The output contains 2, 3, 4 from each of the three paths
    assertTrue(tester.contains(2))
    assertTrue(tester.contains(3))
    assertTrue(tester.contains(4))

    // 3. The output contains a single atom
    assertEquals(1, tester.receiveAllWrapped().filter { case tester.Atom => true; case _ => false }.size)
