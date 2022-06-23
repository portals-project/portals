package pods.workflows

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

    val builder = Workflows
      .builder()
      .withName("wf")

    val source = builder
      .source[Int]()
      .withName("input")

    val fromSource1 = builder
      .from(source)
      .map(_ + 1)

    val fromSource2 = builder
      .from(source)
      .map(_ + 2)

    val merged = builder
      .merge(fromSource1, fromSource2)
      .withName("merged")

    val sink = builder
      .from(merged)
      .sink[Int]()
      .withName("output")

    val wf = builder.build()

    val system = Systems.local()
    system.launch(wf)

    // create a test environment IRef
    val testIRef = TestUtils.TestIStreamRef[Int]()

    val iref: IStreamRef[Int] = system.registry("wf/input").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/output").resolve()

    val _ = oref.subscribe(testIRef)
    
    iref.submit(1)
    iref.fuse()

    system.shutdown()

    assertTrue(testIRef.contains(2) && testIRef.contains(3))
    assertFalse(testIRef.contains(1))
