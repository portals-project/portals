package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

@RunWith(classOf[JUnit4])
class AtomAlignmentTest:

  @Ignore
  @Test
  def testSingleSourceSingleSink(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders.application("application")

    val testData = List.range(0, 128).grouped(1).toList
    val generator = builder.generators.fromListOfLists("generator", testData)

    val workflow = builder
      .workflows[Int, Int]("wf")
      .source[Int](generator.stream)
      .sink()
      .freeze()

    val _ = tester.workflow(workflow.stream, builder)

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    // ASTPrinter.println(application)

    system.stepAll()
    system.shutdown()

    testData.flatten.foreach { i =>
      println(i)
      tester.receiveAssert(i)
    }

  // @Test
  // def testDoubleSourceDoubleSink(): Unit =
  //   val builder = Builders.application("application")

  //   val workflowBuilder = builder.workflows[Int, Int]("wf")
  //   val _ = workflowBuilder
  //     .source[Int]()
  //     .withName("input1")
  //     .sink()
  //     .withName("output1")

  //   val _ = workflowBuilder
  //     .source[Int]()
  //     .withName("input2")
  //     .sink()
  //     .withName("output2")

  //   val application = builder.build()

  //   val system = Systems.syncLocal()
  //   system.launch(application)

  //   val iref1: IStreamRef[Int] = system.registry("wf/input1").resolve()
  //   val oref1: OStreamRef[Int] = system.registry.orefs("wf/output1").resolve()
  //   val iref2: IStreamRef[Int] = system.registry("wf/input2").resolve()
  //   val oref2: OStreamRef[Int] = system.registry.orefs("wf/output2").resolve()

  //   // create a test environment IRef
  //   val testIRef1 = TestUtils.TestPreSubmitCallback[Int]()
  //   val testIRef2 = TestUtils.TestPreSubmitCallback[Int]()
  //   oref1.setPreSubmitCallback(testIRef1)
  //   oref2.setPreSubmitCallback(testIRef2)

  //   val n = 128
  //   (0 until n).foreach { i =>
  //     iref1.submit(i)
  //     iref2.submit(i)
  //   }

  //   iref1.fuse()
  //   iref2.fuse()

  //   system.stepAll()
  //   system.shutdown()

  //   (0 until n).foreach { i =>
  //     assertEquals(Some(i), testIRef1.receive())
  //     assertEquals(Some(i), testIRef2.receive())
  //   }

  //   assertEquals(true, testIRef1.isEmpty())
  //   assertEquals(true, testIRef2.isEmpty())

  // @Test
  // def testDoubleSourceSingleSink(): Unit =
  //   val builder = Portals
  //     .builder("wf")

  //   val source1 = builder
  //     .source[Int]()
  //     .withName("input1")

  //   val source2 = builder
  //     .source[Int]()
  //     .withName("input2")

  //   val _ = builder
  //     .merge(source1, source2)
  //     .sink()
  //     .withName("output")

  //   val wf = builder.build()

  //   val system = Systems.syncLocal()
  //   system.launch(wf)

  //   val iref1: IStreamRef[Int] = system.registry("wf/input1").resolve()
  //   val iref2: IStreamRef[Int] = system.registry("wf/input2").resolve()
  //   val oref: OStreamRef[Int] = system.registry.orefs("wf/output").resolve()

  //   // create a test environment IRef
  //   val testIRef = TestUtils.TestPreSubmitCallback[Int]()
  //   oref.setPreSubmitCallback(testIRef)

  //   val n = 128
  //   (0 until n).foreach { i =>
  //     iref1.submit(i)
  //     iref2.submit(i)
  //   }

  //   iref1.fuse()
  //   iref2.fuse()

  //   system.stepAll()
  //   system.shutdown()

  //   (0 until n).foreach { i =>
  //     assertEquals(Some(i), testIRef.receive())
  //   }
  //   (0 until n).foreach { i =>
  //     assertEquals(Some(i), testIRef.receive())
  //   }

  //   assertEquals(true, testIRef.isEmpty())

  @Test
  def testDiamond(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("application")

    val testData = List.range(0, 256).grouped(1).toList
    val generator = builder.generators.fromListOfLists("generator", testData)

    val wfb = builder
      .workflows[Int, Int]("wf")

    val source = wfb
      .source[Int](generator.stream)

    val fromSource1 = source
      .identity()

    val fromSource2 = source
      .identity()

    val merged = fromSource1
      .union(fromSource2)

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

    testData.foreach { atom =>
      atom.foreach { event =>
        tester.receiveAssert(event)
        tester.receiveAssert(event)
      }
    }

  @Test
  def testDiamond2(): Unit =
    import portals.DSL.*

    val tester = new TestUtils.Tester[Int]()

    val builder = ApplicationBuilders
      .application("application")

    val testData = List.range(0, 256).grouped(128).toList
    val generator = builder.generators.fromListOfLists("generator", testData)

    val wfb = builder
      .workflows[Int, Int]("wf")

    val source = wfb
      .source[Int](generator.stream)

    val fromSource1 = source
      .identity()

    val fromSource2 = source
      .identity()

    val merged = fromSource1
      .union(fromSource2)

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

    val firstAtom = testData(0)
    tester.receiveAtom().get.foreach { event =>
      assertTrue(firstAtom.contains(event))
    }
    val secondAtom = testData(1)
    tester.receiveAtom().get.foreach { event =>
      assertTrue(secondAtom.contains(event))
    }
