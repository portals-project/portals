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

/** Inventory for the Shopping Cart example.
  *
  * @see
  *   for more information on how to run this example:
  *   [[portals.distributed.server.examples.shoppingcart.ShoppingCart]]
  *
  * @see
  *   [[portals.examples.shoppingcart.ShoppingCart]]
  */
object Inventory extends SubmittableApplication:
  override def apply(): Application =
    PortalsApp("Inventory"):
      val inventoryOpsGenerator = Generators.generator(ShoppingCartData.inventoryOpsGenerator)
      val portal = Portal[InventoryReqs, InventoryReps]("inventory", keyFrom)
      val inventory = Workflows[InventoryReqs, Nothing]("inventory")
        .source(inventoryOpsGenerator.stream)
        .key(keyFrom(_))
        .logger()
        .task(InventoryTask(portal))
        .withName("inventory")
        .sink()
        .freeze()
