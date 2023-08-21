package portals.distributed.examples.shoppingcart

import portals.api.dsl.DSL
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.Application
import portals.distributed.SubmittableApplication
import portals.examples.shoppingcart.tasks.*
import portals.examples.shoppingcart.ShoppingCartEvents.*

/** Analytics for the Shopping Cart example.
  *
  * @see
  *   for more information on how to run this example:
  *   [[portals.distributed.examples.shoppingcart.ShoppingCart]]
  *
  * @see
  *   [[portals.examples.shoppingcart.ShoppingCart]]
  */
object Analytics extends SubmittableApplication:
  override def apply(): portals.application.Application =
    portals.api.dsl.DSL.PortalsApp("Analytics"):
      val ordersStream = Registry.streams.get[OrderOps]("/Orders/workflows/orders/stream")

      val analyticsportal = Portal[AnalyticsReqs, AnalyticsReps]("analytics", keyFrom)

      val analytics = Workflows[OrderOps, Nothing]("analytics")
        .source(ordersStream)
        .flatMap { case Order(_, CartState(items)) => items }
        .key(keyFrom(_))
        .task(AggregatingTask())
        .key(_ => 0L)
        .task(AnalyticsTask(analyticsportal))
        .logger()
        .nothing()
        .sink()
        .freeze()
