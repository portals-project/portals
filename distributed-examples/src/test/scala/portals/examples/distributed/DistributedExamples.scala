package portals.examples.distributed

import scala.annotation.experimental

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert._
import org.junit.Test

import portals.examples.distributed.actor.PingPongMain

/** Tests that all distributed examples compile and run, does not test that the
  * output is correct.
  */
@experimental
@RunWith(classOf[JUnit4])
class DistributedExamplesTest:

  @Test
  def testFibonacci(): Unit =
    import portals.examples.distributed.actor.FibonacciMain
    FibonacciMain

  @Test
  def testPingPong(): Unit =
    import portals.examples.distributed.actor.FibonacciMain
    PingPongMain

  @Test
  def testShoppingCart(): Unit =
    import portals.examples.distributed.shoppingcart.ShoppingCartMain
    ShoppingCartMain

  @Test
  def testBankAccount(): Unit =
    import portals.examples.distributed.bankaccount.BankAccountMain
    BankAccountMain
