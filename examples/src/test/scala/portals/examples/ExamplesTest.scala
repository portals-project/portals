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
  def testFibonacci(): Unit =
    import portals.examples.actor.FibonacciMain
    FibonacciMain

  @Test
  def testPingPong(): Unit =
    import portals.examples.actor.PingPongMain
    PingPongMain

  @Test
  def testShoppingCart(): Unit =
    import portals.examples.shoppingcart.ShoppingCartMain
    ShoppingCartMain

  @Test
  def testBankAccount(): Unit =
    import portals.examples.bankaccount.BankAccountMain
    BankAccountMain

  @Test
  def testHelloWorld(): Unit =
    import portals.examples.tutorial.HelloWorldMain
    HelloWorldMain()

  @Test
  def testDynamicQuery(): Unit =
    import portals.examples.tutorial.DynamicQuery
    DynamicQuery()

  @Test
  def testIncrementalWordCount(): Unit =
    import portals.examples.tutorial.IncrementalWordCount
    IncrementalWordCount()

  @Test
  def testPortalPingPong(): Unit =
    import portals.examples.tutorial.PortalPingPong
    PortalPingPong()

  @Test
  def testStepByStep(): Unit =
    import portals.examples.tutorial.StepByStep
    StepByStep()

  @Test
  def testWordCount(): Unit =
    import portals.examples.tutorial.WordCount
    WordCount()
