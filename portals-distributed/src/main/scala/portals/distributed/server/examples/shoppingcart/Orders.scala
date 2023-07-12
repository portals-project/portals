package portals.distributed.server.examples.shoppingcart

import portals.api.dsl.DSL
import portals.api.dsl.DSL.*
import portals.api.dsl.DSL.PortalsApp
import portals.api.dsl.ExperimentalDSL.*
import portals.application.Application
import portals.distributed.server.SubmittableApplication
import portals.examples.shoppingcart.tasks.*
import portals.examples.shoppingcart.ShoppingCartData
import portals.examples.shoppingcart.ShoppingCartEvents.*

/** Orders for the Shopping Cart example.
  *
  * @see
  *   for more information on how to run this example:
  *   [[portals.distributed.server.examples.shoppingcart.ShoppingCart]]
  *
  * @see
  *   [[portals.examples.shoppingcart.ShoppingCart]]
  */
object Orders extends SubmittableApplication:
  override def apply(): Application =
    PortalsApp("Orders"):
      val cartStream = Registry.streams.get[OrderOps]("/Cart/workflows/cart/stream")

      val orders = Workflows[OrderOps, OrderOps]("orders")
        .source(cartStream)
        .key(keyFrom(_))
        .task(OrdersTask())
        .withName("orders")
        .sink()
        .freeze()
