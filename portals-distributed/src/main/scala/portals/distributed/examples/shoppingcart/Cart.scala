package portals.distributed.examples.shoppingcart

import portals.api.dsl.DSL
import portals.api.dsl.DSL.*
import portals.api.dsl.DSL.PortalsApp
import portals.api.dsl.ExperimentalDSL.*
import portals.application.Application
import portals.distributed.SubmittableApplication
import portals.examples.shoppingcart.tasks.*
import portals.examples.shoppingcart.ShoppingCartData
import portals.examples.shoppingcart.ShoppingCartEvents.*

/** Cart for the Shopping Cart example.
  *
  * @see
  *   for more information on how to run this example:
  *   [[portals.distributed.examples.shoppingcart.ShoppingCart]]
  *
  * @see
  *   [[portals.examples.shoppingcart.ShoppingCart]]
  */
object Cart extends SubmittableApplication:
  override def apply(): Application =
    PortalsApp("Cart"):
      val cartOpsGenerator = Generators.generator(ShoppingCartData.cartOpsGenerator)
      val portal = Registry.portals.get[InventoryReqs, InventoryReps]("/Inventory/portals/inventory")

      val cart = Workflows[CartOps, OrderOps]("cart")
        .source(cartOpsGenerator.stream)
        .key(keyFrom(_))
        .task(CartTask(portal))
        .withName("cart")
        .sink()
        .freeze()
