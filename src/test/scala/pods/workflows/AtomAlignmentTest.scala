package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Ignore

@RunWith(classOf[JUnit4])
class AtomAlignmentTest:

  @Test
  def testSingleSourceSingleSink(): Unit =
    val builder = Workflows
      .builder()
      .withName("wf")
      
    val _ = builder
      .source[Int]()
      .withName("input")
      .sink()
      .withName("output")

    val wf = builder.build()

    val system = Systems.local()
    system.launch(wf)

    val iref: IStreamRef[Int] = system.registry("wf/input").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/output").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestIStreamRef[Int]()
    oref.subscribe(testIRef)

    val n = 128
    (0 until n).foreach { i =>
      (0 until n).foreach { j => 
        iref.submit(j)
      }
      iref.fuse()
    }

    system.shutdown()
    
    (0 until n).foreach { i =>
      (0 until n).foreach { j =>
        assertEquals(Some(j), testIRef.receive())
      }
    }

  @Test
  def testDoubleSourceDoubleSink(): Unit =
    val builder = Workflows
      .builder()
      .withName("wf")
      
    val _ = builder
      .source[Int]()
      .withName("input1")
      .sink()
      .withName("output1")

    val _ = builder
      .source[Int]()
      .withName("input2")
      .sink()
      .withName("output2")

    val wf = builder.build()

    val system = Systems.local()
    system.launch(wf)

    
    val iref1: IStreamRef[Int] = system.registry("wf/input1").resolve()
    val oref1: OStreamRef[Int] = system.registry.orefs("wf/output1").resolve()
    val iref2: IStreamRef[Int] = system.registry("wf/input2").resolve()
    val oref2: OStreamRef[Int] = system.registry.orefs("wf/output2").resolve()

    // create a test environment IRef
    val testIRef1 = TestUtils.TestIStreamRef[Int]()
    val testIRef2 = TestUtils.TestIStreamRef[Int]()
    oref1.subscribe(testIRef1)
    oref2.subscribe(testIRef2)

    val n = 128
    (0 until n).foreach { i =>
      iref1.submit(i)
      iref2.submit(i)
    }

    iref1.fuse()
    iref2.fuse()

    system.shutdown()
    
    (0 until n).foreach { i =>
      assertEquals(Some(i), testIRef1.receive())
      assertEquals(Some(i), testIRef2.receive())
    }
    
    assertEquals(true, testIRef1.isEmpty())
    assertEquals(true, testIRef2.isEmpty())

  @Test
  def testDoubleSourceSingleSink(): Unit =
    val builder = Workflows
      .builder()
      .withName("wf2")
      
    val source1 = builder
      .source[Int]()
      .withName("input1")

    val source2 = builder
      .source[Int]()
      .withName("input2")

    val _ = builder
      .merge(source1, source2)
      .sink()
      .withName("output")

    val wf = builder.build()

    val system = Systems.local()
    system.launch(wf)

    val iref1: IStreamRef[Int] = system.registry("wf2/input1").resolve()
    val iref2: IStreamRef[Int] = system.registry("wf2/input2").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf2/output").resolve()

    val fwd1 = TestUtils.forwardingWorkflow[Int, Int]("fwd1")(using system)
    val fwd2 = TestUtils.forwardingWorkflow[Int, Int]("fwd2")(using system)

    fwd1._2.subscribe(iref1)
    fwd2._2.subscribe(iref2)
    
    // create a test environment IRef
    val testIRef = TestUtils.TestIStreamRef[Int]()
    oref.subscribe(testIRef)

    val n = 128
    (0 until n).foreach { i =>
      fwd1._1.submit(i)
      fwd2._1.submit(i)
    }

    fwd1._1.fuse()
    fwd2._1.fuse()

    system.shutdown()
    
    (0 until n).foreach { i =>
      assertEquals(Some(i), testIRef.receive())
    }
    (0 until n).foreach { i =>
      assertEquals(Some(i), testIRef.receive())
    }
    
    assertEquals(true, testIRef.isEmpty())


  @Test
  def testDiamond(): Unit =
    val builder = Workflows
      .builder()
      .withName("wf")
      
    val source = builder
      .source[Int]()
      .withName("source")

    val split1 = builder
      .from(source)
      .identity()

    val split2 = builder
      .from(source)
      .identity()

    val _ = builder
      .merge(split1, split2)
      .sink()
      .withName("sink")

    val wf = builder.build()

    val system = Systems.local()
    system.launch(wf)

    val iref: IStreamRef[Int] = system.registry("wf/source").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/sink").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestIStreamRef[Int]()
    oref.subscribe(testIRef)

    val n = 256
    (0 until n).foreach { i =>
      iref.submit(i)
      iref.fuse()
    }

    system.shutdown()

    (0 until n).foreach { i =>
      assertEquals(Some(i), testIRef.receive())
      assertEquals(Some(i), testIRef.receive())
    }
    
    assertEquals(true, testIRef.isEmpty())


  @Test
  def testDiamond2(): Unit =
    val builder = Workflows
      .builder()
      .withName("wf")
      
    val source = builder
      .source[Int]()
      .withName("source")

    val split1 = builder
      .from(source)
      .identity()

    val split2 = builder
      .from(source)
      .identity()

    val _ = builder
      .merge(split1, split2)
      .sink()
      .withName("sink")

    val wf = builder.build()

    val system = Systems.local()
    system.launch(wf)

    val iref: IStreamRef[Int] = system.registry("wf/source").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/sink").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestIStreamRef[Int]()
    oref.subscribe(testIRef)

    val n = 256
    (0 until n).foreach { i =>
      iref.submit(i)
    }
    iref.fuse()

    (0 until n).foreach { i =>
      iref.submit(i)
    }
    iref.fuse()

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