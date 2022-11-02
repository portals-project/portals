package portals.examples

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Test

import portals.*
import portals.test.*

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
    val generator = builder.generators.fromList(List(message))

    val _ = builder
      .workflows[String, String]()
      .source(generator.stream)
      .map { x => x }
      // .logger()
      .task(tester.task)
      .sink()
      .freeze()

    val application = builder.build()

    // ASTPrinter.println(application)

    val system = Systems.test()

    system.launch(application)

    system.stepUntilComplete()
    system.shutdown()

    tester.receiveAssert(message)
