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
    import portals.DSL.*

    val tester = new TestUtils.Tester[String]()

    val builder = ApplicationBuilders
      .application("app")

    val message = "Hello, World!"
    val generator = builder.generators.fromList("hw", List(message))

    val _ = builder
      .workflows[String, String]("wf")
      .source[String](generator.stream)
      .map[String] { x => x }
      // .logger()
      .task(tester.task)
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.syncLocal()
    system.launch(application)

    system.stepAll()
    system.shutdown()

    tester.receiveAssert(message)
