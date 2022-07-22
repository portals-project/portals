package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class BasicTest:

  @Test
  def testIdentity(): Unit =

    val builder = ApplicationBuilders.application("application")

    val source = builder
      .workflows[Int, Int]("wf")
      .source[Int]()
      .withName("source")
      .identity()
      .sink()
      .withName("sink")

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    val iref: IStreamRef[Int] = system.registry("/application/wf/source").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("/application/wf/sink").resolve()

    val testIRef = TestUtils.TestPreSubmitCallback[Int]()
    oref.setPreSubmitCallback(testIRef)

    val testDatas = (0 until 128).toList
    testDatas.foreach { i =>
      iref.submit(i)
      iref.fuse()
    }

    system.stepAll()
    system.shutdown()

    assertEquals(testDatas, testIRef.receiveAll())
