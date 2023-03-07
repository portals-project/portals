package portals.examples

import scala.annotation.experimental

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Test

/** Tests that all examples compile and run, does not test that the output is
  * correct.
  */
@experimental
@RunWith(classOf[JUnit4])
class ExamplesTest:

  @Test
  def testHelloWorld(): Unit =
    HelloWorldMain()

  @Test
  def testDynamicQuery(): Unit =
    DynamicQuery()

  @Test
  def testIncrementalWordCount(): Unit =
    IncrementalWordCount()

  @Test
  def testPortalPingPong(): Unit =
    PortalPingPong()

  @Test
  def testStepByStep(): Unit =
    StepByStep()

  @Test
  def testWordCount(): Unit =
    WordCount()
