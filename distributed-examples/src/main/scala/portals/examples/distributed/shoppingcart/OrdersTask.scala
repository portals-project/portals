package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*

import ShoppingCartEvents.*

@experimental
class OrdersTask extends CustomProcessorTask[OrderOps, Nothing]:
  import portals.DSL.*

  override def onNext(using Context)(event: OrderOps): Unit =
    ctx.log.info(s"Ordering $event")
