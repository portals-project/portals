package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

// Verify that diamond task structure works.
@RunWith(classOf[JUnit4])
class DiamondTaskGraphTest:
  @Test
  def testDiamondTaskGraph() = 
    import pods.workflows.*
    import java.util.concurrent.atomic.AtomicReference

    val output = new AtomicReference[List[Any]](List.empty)
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
      // The output is not fused into an atom yet.

    val builder2 = Workflows
      .builder()
      .withName("wf2")

    val flow2 = builder2
      .source[Int]()
      .withName("input")
      .processor[Int] {
        ctx ?=> s =>
          var continue = true
          while continue do
            val oldV = output.get
            val newV = s :: oldV
            if (output.compareAndSet(oldV, newV))
            continue = false
      }
      .sink()
      .withName("output")

    val wf = builder.build()
    val wf2 = builder2.build()

    val system = Systems.local()
    system.launch(wf)
    system.launch(wf2)

    val iref2: IStreamRef[Int] = system.registry("wf2/input").resolve()
    val oref: OStreamRef[Int] = system.registry.orefs("wf/output").resolve()

    val _ = oref.subscribe(iref2)

    val iref: IStreamRef[Int] = system.registry("wf/input").resolve()
    iref.submit(1)
    iref.fuse()

    system.shutdown()

    println(output.get)

    assertTrue((output.get() contains 3) && (output.get() contains 2))
    assertFalse(output.get() contains 1)
