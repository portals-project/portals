package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._


@RunWith(classOf[JUnit4])
class AtomTest:

  @Test
  def basicAtomTest(): Unit = 
    import pods.workflows.DSL.*

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

    val wf = builder.build()

    val system = Systems.local()

    system.launch(wf)
    
    val iref = system.registry[String]("wf/input").resolve() 
    val oref = system.registry.orefs[String]("wf/output").resolve()
    
    // create a test environment IRef
    val testIRef = TestUtils.TestIStreamRef[String]()
    
    // subscribe the testIref to the workflow
    oref.subscribe(testIRef)

    val testData = "testData"
    iref ! testData

    // let us wait for 1 second
    Thread.sleep(1000)

    // nothing is happening yet, the atom is not complete (we need to fuse it first)
    assertTrue(testIRef.isEmpty())

    // now we trigger the atom barrier which will trigger the fusion
    iref ! FUSE 
    system.shutdown()
    
    // we should now expect to observe some output from the workflow
    testIRef.receiveAssert(testData)
    