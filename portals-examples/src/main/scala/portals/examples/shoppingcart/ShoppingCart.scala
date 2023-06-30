package portals.examples.shoppingcart

import scala.annotation.experimental

import portals.api.dsl.CustomTask
import portals.api.dsl.DSL
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.Application
import portals.examples.shoppingcart.tasks.*
import portals.examples.shoppingcart.ShoppingCartEvents.*

////////////////////////////////////////////////////////////////////////////////
// Shopping Cart
////////////////////////////////////////////////////////////////////////////////
object ShoppingCart:
  def app: Application = PortalsApp("shopping-cart") {
    val inventoryOpsGenerator = Generators.generator(ShoppingCartData.inventoryOpsGenerator)

    val cartOpsGenerator = Generators.generator(ShoppingCartData.cartOpsGenerator)

    val portal = Portal[InventoryReqs, InventoryReps]("inventory", keyFrom)

    val analyticsportal = Portal[AnalyticsReqs, AnalyticsReps]("analytics", keyFrom)

    val cart = Workflows[CartOps, OrderOps]("cart")
      .source(cartOpsGenerator.stream)
      .key(keyFrom(_))
      .task(CartTask(portal))
      .withName("cart")
      .sink()
      .freeze()

    val inventory = Workflows[InventoryReqs, Nothing]("inventory")
      .source(inventoryOpsGenerator.stream)
      .key(keyFrom(_))
      .task(InventoryTask(portal))
      .withName("inventory")
      .sink()
      .freeze()

    val orders = Workflows[OrderOps, OrderOps]("orders")
      .source(cart.stream)
      .key(keyFrom(_))
      .task(OrdersTask())
      .withName("orders")
      .sink()
      .freeze()

    val analytics = Workflows[OrderOps, Nothing]("analyics")
      .source(orders.stream)
      .flatMap { case Order(_, CartState(items)) => items }
      .key(keyFrom(_))
      .task(AggregatingTask())
      .key(_ => 0L)
      .task(AnalyticsTask(analyticsportal))
      .logger()
      .nothing()
      .sink()
      .freeze()
  }
  end app // def
