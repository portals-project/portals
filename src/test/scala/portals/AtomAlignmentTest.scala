package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

@RunWith(classOf[JUnit4])
class AtomAlignmentTest:

  @Test
  def testSingleSourceSingleSink(): Unit =
    val builder = Builders.application("application")

    val _ = builder
      .workflows[Int, Int]("wf")
      .source[Int]()
      .withName("input")
      .sink()
      .withName("output")

    val wf = builder.build()

    val system = Systems.syncLocal()
    system.launch(wf)

    val iref: IStreamRef[Int] = system.registry("wf/input").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/output").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestPreSubmitCallback[Int]()
    oref.setPreSubmitCallback(testIRef)

    val n = 128
    (0 until n).foreach { i =>
      (0 until n).foreach { j =>
        iref.submit(j)
      }
      iref.fuse()
    }

    system.stepAll()
    system.shutdown()

    (0 until n).foreach { i =>
      (0 until n).foreach { j =>
        assertEquals(Some(j), testIRef.receive())
      }
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
    val builder = Builders.application("application")

    val source = builder
      .workflows[Int, Int]("wf")
      .source[Int]()
      .withName("source")

    val split1 = source
      .identity()

    val split2 = source
      .identity()

    val _ = split1
      .union(split2)
      .sink()
      .withName("sink")

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    val iref: IStreamRef[Int] = system.registry("wf/source").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/sink").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestPreSubmitCallback[Int]()
    oref.setPreSubmitCallback(testIRef)

    val n = 256
    (0 until n).foreach { i =>
      iref.submit(i)
      iref.fuse()
    }

    system.stepAll()
    system.shutdown()

    (0 until n).foreach { i =>
      assertEquals(Some(i), testIRef.receive())
      assertEquals(Some(i), testIRef.receive())
    }

    assertEquals(true, testIRef.isEmpty())

  @Test
  def testDiamond2(): Unit =
    val builder = Builders.application("application")

    val source = builder
      .workflows[Int, Int]("wf")
      .source[Int]()
      .withName("source")

    val split1 = source
      .identity()

    val split2 = source
      .identity()

    val _ = split1
      .union(split2)
      .sink()
      .withName("sink")

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    val iref: IStreamRef[Int] = system.registry("wf/source").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/sink").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestPreSubmitCallback[Int]()
    oref.setPreSubmitCallback(testIRef)

    val n = 256
    (0 until n).foreach { i =>
      iref.submit(i)
    }
    iref.fuse()

    (0 until n).foreach { i =>
      iref.submit(i)
    }
    iref.fuse()

    system.stepAll()
    system.shutdown()

    var receives = Set.empty[Int]
    (0 until n).foreach { i =>
      receives += testIRef.receive().get
      receives += testIRef.receive().get
    }
    assertEquals(receives, (0 until n).toSet)

    receives = Set.empty[Int]
    (0 until n).foreach { i =>
      receives += testIRef.receive().get
      receives += testIRef.receive().get
    }
    assertEquals(receives, (0 until n).toSet)

    assertEquals(true, testIRef.isEmpty())
