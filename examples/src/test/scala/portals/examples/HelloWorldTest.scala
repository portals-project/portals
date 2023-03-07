package portals.examples

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Test

import portals.api.builder.ApplicationBuilder
import portals.test.TestUtils
import portals.system.Systems

@RunWith(classOf[JUnit4])
class HelloWorldTest:

  @Test
  def testHelloWorld(): Unit =
    val application = HelloWorld.app
    val system = Systems.interpreter()
    system.launch(application)
    system.stepUntilComplete()
    system.shutdown()
