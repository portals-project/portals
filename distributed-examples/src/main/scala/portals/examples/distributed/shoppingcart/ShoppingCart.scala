package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*

////////////////////////////////////////////////////////////////////////////////
// Shopping Cart
////////////////////////////////////////////////////////////////////////////////
@experimental
object ShoppingCart:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  import ShoppingCartEvents.*

  def app: Application = PortalsApp("shopping-cart") {
    val inventoryops = Generators.fromList(Data.inventoryopsList)
    val cartops = Generators.fromListOfLists(Data.cartopsList)

    val portal = Portal[InventoryReqs, InventoryReps]("inventory", { x => keyFrom(x) })

    val cart = Workflows[CartOps, OrderOps]("cart")
      .source(cartops.stream)
      .key(keyFrom(_))
      .task(CustomTask.asker(portal)(() => new CartTask(portal)))
      .withName("cart")
      .sink()
      .freeze()

    val inventory = Workflows[InventoryReqs, Nothing]("inventory")
      .source(inventoryops.stream)
      .key(keyFrom(_))
      .task(CustomTask.replier(portal)(() => new InventoryTask(portal)))
      .withName("inventory")
      .sink()
      .freeze()

    val orders = Workflows[OrderOps, Nothing]("orders")
      .source(cart.stream)
      .key(keyFrom(_))
      .task(CustomTask.processor(() => new OrdersTask()))
      .withName("orders")
      .sink()
      .freeze()
  }
  end app // def
