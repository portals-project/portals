package portals.examples.shoppingcart

import scala.annotation.experimental

import portals.system.Systems

object ShoppingCartMain extends App:
  val system = Systems.interpreter()
  val _ = system.launch(ShoppingCart.app)
  // -- to execute for longer time, uncomment the following two lines: --
  // val now = System.currentTimeMillis()
  // while System.currentTimeMillis() - now < 10_000_000 do system.stepUntilComplete()
  system.stepUntilComplete()
  system.shutdown()
