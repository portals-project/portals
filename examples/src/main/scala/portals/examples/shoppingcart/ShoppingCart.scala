package portals.examples.shoppingcart

import scala.annotation.experimental

import portals.api.dsl.CustomTask
import portals.api.dsl.DSL
import portals.api.dsl.ExperimentalDSL
import portals.application.Application

////////////////////////////////////////////////////////////////////////////////
// Shopping Cart
////////////////////////////////////////////////////////////////////////////////
@experimental
object ShoppingCart:
  import portals.api.dsl.DSL.*
  import portals.api.dsl.ExperimentalDSL.*
  import portals.examples.shoppingcart.ShoppingCartEvents.*
  import portals.examples.shoppingcart.ShoppingCartTasks.*

  private val _rand = scala.util.Random()
  inline def sample_1024(): Boolean = _rand.nextInt(1024) < 1

  def app: Application = PortalsApp("shopping-cart") {
    val inventoryops = Generators.fromIteratorOfIterators(ShoppingCartData.inveOpsIter)
    val cartops = Generators.fromIteratorOfIterators(ShoppingCartData.cartOpsIter)

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
      .filter { _ => sample_1024() }
      .logger()
      .task(CustomTask.processor(() => new OrdersTask()))
      .withName("orders")
      .sink()
      .freeze()
  }
  end app // def
