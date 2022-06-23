package pods.workflows

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

/** Atom Tests
  *
  * Atoms are a unit of computation/data. In some way, atoms can be seen as a 
  * batch of events. A workflow processes an atom of events atomically. That is,
  * a workflow processes the atoms in some sequential total-order. No two atoms
  * are processed concurrently by a workflow (at least on the logical level).
  * 
  * The dual to atoms is that which separates atoms. Atoms are separated by 
  * atom-barriers. These atom-barriers are in turn also atomic serializable 
  * events. The time of the atom-barrier passing is the event onAtomComplete()
  * which is triggered on event-handling tasks within the workflow.
  * 
  * These tests show the basic use of atoms. For more advanced use, such as
  * achieving serializable updates, we refer to other tests.
  */

/** Basic Atom test
  *
  * This test creates a workflow that simply forwards any of the events from
  * its source to its sink. We then ingest some test data, and will find that
  * nothing is happening yet, as we have not fused an atom (and so the atom is 
  * fully computed / output yet). After a short wait we fuse the atom and 
  * suddenly observe some output from the workflow, as we would expect.
  */
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
    