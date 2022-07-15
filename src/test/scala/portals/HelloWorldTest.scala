package portals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._

/** HelloWorld Test
  *
  * This is a collection of canonical hello world tests.
  */

/** Logged Hello World
  *
  * This example creates a workflow that prints all the ingested events to the
  * logger. We submit the event containing the message "Hello, World!" and 
  * expect it to be printed.
  */
@RunWith(classOf[JUnit4])
class HelloWorldTest:

  @Test
  def testHelloWorld(): Unit =
    val helloWorld = "Hello, World!"

    val builder = Portals
      .builder("wf")

    val flow = builder
      .source[String]()
      .withName("input")
      .map[String] { x => x }
      // .withLogger() // print the output to logger
      .sink()
      .withName("output")

    val wf = builder.build()

    val system = Systems.syncLocal()
    system.launch(wf)

    val iref: IStreamRef[String] = system.registry("wf/input").resolve()
    val oref: OStreamRef[String] = system.registry.orefs("wf/output").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestPreSubmitCallback[String]()
    oref.setPreSubmitCallback(testIRef)


    iref.submit(helloWorld)
    iref.fuse()

    system.stepAll()
    system.shutdown()

    testIRef.receiveAssert(helloWorld)