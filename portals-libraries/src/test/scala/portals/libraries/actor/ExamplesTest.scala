package portals.libraries.actor

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
  def testFibonacci(): Unit =
    import portals.libraries.actor.examples.FibonacciMain
    FibonacciMain

  @Test
  def testPingPong(): Unit =
    import portals.libraries.actor.examples.PingPongMain
    PingPongMain

  @Test
  def testCountingActor(): Unit =
    import portals.libraries.actor.examples.CountingActorMain
    CountingActorMain
