package pods.workflows.examples

/** Atom Examples
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
  * These examples show the basic use of atoms. For more advanced use, such as
  * achieving serializable updates, we refer to other examples.
  */

/** Basic Atom Example
  *
  * This example creates a workflow that simply forwards any of the events from
  * its source to its sink. We also create another workflow, a logging workflow, 
  * that will write any input to a logger. And then, we connect the two 
  * workflows. We then ingest some test data, and will find that this does not 
  * print anything just yet, as we have not fused an atom (and so the atom is 
  * fully computed / output yet). After a short wait we fuse the atom and 
  * suddenly see the output, as we would expect.
  */
@main def BasicAtomExample = 
  import pods.workflows.*
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

  val wf = builder
    .build()

  given system: SystemContext = Systems.local()

  system.launch(wf)

  val iref = system.registry[String]("wf/input").resolve() 
  val oref = system.registry.orefs[String]("wf/output").resolve()

  // create a logger and subscribe it to the oref of the workflow
  val logger = Utils.loggingWorkflow[String]()
  oref.subscribe(logger)

  // ingest some test data into the input
  val testData = "testData"
  iref ! testData

  // nothing is happening yet, the atom is not complete (we need to fuse it first)
  logger ! "Nothing is logged as the atom has not been fused yet."

  logger ! "Let us wait for 1 second."
  Thread.sleep(1000)
  
  logger ! "Now we trigger the atom barrier which will trigger the fusion."
  logger ! "We should now expect to see the output: " + testData

  // fuse the atom, expect this to trigger the computation
  iref ! FUSE 

  system.shutdown()