package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

// Verify that atom fusing works as expected.
@RunWith(classOf[JUnit4])
class AtomTest:
  @Test
  def testAtom = 
    import java.util.concurrent.atomic.AtomicReference
    import pods.workflows.DSL.*

    val output = new AtomicReference[List[Any]](List.empty)
    
    val builder = Workflows
      .builder()
      .withName("wf")

    // simple workflow that forwards any input to the output
    val flow = builder
      .source[String]()
      .withName("input")
      .identity()
      .sink()
      .withName("output")

    val builder2 = Workflows
      .builder()
      .withName("wf2")

    val flow2 = builder2
      .source[String]()
      .withName("input")
      .processor[String] {
        ctx ?=> s =>
          var continue = true
          while continue do
            val oldV = output.get
            val newV = s :: oldV
            if output.compareAndSet(oldV, newV) then
              continue = false
      }
      .sink()
      .withName("output")

    val wf = builder.build()
    val wf2 = builder2.build()

    given system: SystemContext = Systems.local()

    system.launch(wf)
    system.launch(wf2)

    val iref = system.registry[String]("wf/input").resolve() 
    val oref = system.registry.orefs[String]("wf/output").resolve()
    val iref2 = system.registry[String]("wf2/input").resolve()

    oref.subscribe(iref2)

    // ingest some test data into the input
    val testData = "testData"
    iref ! testData

    println(output.get)
    assertTrue(output.get.isEmpty)
    Thread.sleep(1000)

    // fuse the atom, expect this to trigger the computation
    iref ! FUSE 
    system.shutdown()
    println(output.get)
    assertTrue(output.get.contains(testData))
