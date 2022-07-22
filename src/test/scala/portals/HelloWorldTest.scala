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
  * This example creates a workflow that prints all the ingested events to the logger. We submit the event containing
  * the message "Hello, World!" and expect it to be printed.
  */
@RunWith(classOf[JUnit4])
class HelloWorldTest:

  @Test
  def testHelloWorld(): Unit =
    val helloWorld = "Hello, World!"

    val builder = ApplicationBuilders
      .application("app")

    val workflow = builder.workflows[String, String]("wf")

    val flow = workflow
      .source[String]("src")
      .map[String] { x => x }
      .withName("map")
      // .logger()
      .sink("sink")

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    val iref: IStreamRef[String] = system.registry("/app/wf/src").resolve()
    val oref: OStreamRef[String] = system.registry.orefs("/app/wf/sink").resolve()

    // create a test environment IRef
    val testIRef = TestUtils.TestPreSubmitCallback[String]()
    oref.setPreSubmitCallback(testIRef)

    iref.submit(helloWorld)
    iref.fuse()

    system.stepAll()
    system.shutdown()

    testIRef.receiveAssert(helloWorld)
