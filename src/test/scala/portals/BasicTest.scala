package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

@RunWith(classOf[JUnit4])
class BasicTest:

  @Test
  def testDiamond(): Unit = 

    val builder = Workflows
      .builder()
      .withName("wf")

    val source = builder
      .source[Int]()
      .withName("input")
      .identity()

    val fromSource1 = builder
      .from(source)
      .identity()

    val fromSource2 = builder
      .from(source)
      .identity()

    val merged = builder
      .merge(fromSource1, fromSource2)
      .sink[Int]()
      .withName("output")

    val wf = builder.build()

    val system = Systems.syncLocal()
    system.launch(wf)

    
    val iref: IStreamRef[Int] = system.registry("wf/input").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/output").resolve()
    
    val testIRef = TestUtils.TestPreSubmitCallback[Int]()
    oref.setPreSubmitCallback(testIRef)

    val testDatas = (0 until 128).toList
    testDatas.foreach { i =>
      iref.submit(i)
      iref.fuse()
    }

    Thread.sleep(500)
    system.stepAll()
    system.shutdown()
    Thread.sleep(500)

    val expected = testDatas.flatMap { i => List(i, i) }

    assertEquals(expected, testIRef.receiveAll())


  @Test
  def testIdentity(): Unit = 

    val builder = Workflows
      .builder()
      .withName("wf")

    val source = builder
      .source[Int]()
      .withName("source")
      .identity()
      .sink()
      .withName("sink")

    val wf = builder.build()

    val system = Systems.syncLocal()
    system.launch(wf)

    
    val iref: IStreamRef[Int] = system.registry("wf/source").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/sink").resolve()
    
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