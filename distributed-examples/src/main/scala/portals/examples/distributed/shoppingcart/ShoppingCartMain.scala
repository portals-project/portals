package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*

@experimental
object ShoppingCartMain extends App:
  val app = ShoppingCart.app

  // ASTPrinter.println(app)

  val system = Systems.test(2)

  val _ = system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
