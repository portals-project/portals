package portals.examples.shoppingcart.tasks

import scala.annotation.experimental

import portals.api.dsl.CustomAskerTask
import portals.api.dsl.CustomProcessorTask
import portals.api.dsl.CustomReplierTask
import portals.api.dsl.DSL.*
import portals.application.task.*
import portals.application.AtomicPortalRef
import portals.application.AtomicPortalRefKind
import portals.examples.shoppingcart.ShoppingCartConfig
import portals.examples.shoppingcart.ShoppingCartEvents.*

object OrdersTask:
  type I = OrderOps
  type O = OrderOps
  type Req = Nothing
  type Rep = Nothing
  type Context = MapTaskContext[OrderOps, OrderOps]
  type Task = GenericTask[OrderOps, OrderOps, Nothing, Nothing]

  def onNext(event: OrderOps)(using Context): OrderOps =
    if ShoppingCartConfig.LOGGING then ctx.log.info(s"Order for $event created")
    event

  def apply(): Task =
    Tasks.map(onNext)
end OrdersTask // class
