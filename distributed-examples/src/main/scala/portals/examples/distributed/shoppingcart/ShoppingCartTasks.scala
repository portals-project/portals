package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*
import portals.examples.distributed.shoppingcart.ShoppingCartEvents.*

object ShoppingCartTasks:
  @experimental
  class CartTask(portal: AtomicPortalRefKind[InventoryReqs, InventoryReps])
      extends CustomAskerTask[CartOps, OrderOps, InventoryReqs, InventoryReps]:
    import portals.DSL.*
    import portals.ExperimentalDSL.*

    lazy val state: StatefulTaskContext ?=> PerKeyState[CartState] =
      PerKeyState[CartState]("state", CartState.zero)

    // format: off
    private def addToCart(event: AddToCart)(using AskerTaskContext[CartOps, OrderOps, InventoryReqs, InventoryReps]): Unit =
      val resp = ask(portal)(Get(event.item))
      resp.await { resp.value.get match
        case GetReply(item, success) =>
          if success then
            state.set(state.get().add(item))
            if ShoppingCartConfig.LOGGING then ctx.log.info(s"User ${event.user} added $item to cart")
          else 
            if ShoppingCartConfig.LOGGING then ctx.log.info(s"User ${event.user} tried to add $item to cart, but it was not in inventory")
        case _ => 
          if ShoppingCartConfig.LOGGING then ctx.log.info("Unexpected response")
          ???
      }
    // format: on

    private def removeFromCart(event: RemoveFromCart)(using
        AskerTaskContext[CartOps, OrderOps, InventoryReqs, InventoryReps]
    ): Unit =
      ask(portal)(Put(event.item))
      if ShoppingCartConfig.LOGGING then ctx.log.info(s"User ${event.user} removed ${event.item} from cart")

    private def checkout(event: Checkout)(using
        AskerTaskContext[CartOps, OrderOps, InventoryReqs, InventoryReps]
    ): Unit =
      val cart = state.get()
      ctx.emit(Order(event.user, cart))
      state.del()
      if ShoppingCartConfig.LOGGING then ctx.log.info(s"Checking out ${event.user} with cart $cart")

    override def onNext(using AskerTaskContext[CartOps, OrderOps, InventoryReqs, InventoryReps])(event: CartOps): Unit =
      event match
        case event: AddToCart => addToCart(event)
        case event: RemoveFromCart => removeFromCart(event)
        case event: Checkout => checkout(event)
  end CartTask // class

  @experimental
  class InventoryTask(portal: AtomicPortalRefKind[InventoryReqs, InventoryReps])
      extends CustomReplierTask[InventoryReqs, Nothing, InventoryReqs, InventoryReps]:
    import portals.DSL.*
    import portals.ExperimentalDSL.*

    lazy val state: StatefulTaskContext ?=> PerKeyState[Int] = PerKeyState[Int]("state", 0)

    def get(e: Get)(using ProcessorTaskContext[InventoryReqs, Nothing]): Unit =
      if ShoppingCartConfig.LOGGING then ctx.log.info(s"Taking ${e.item} from inventory")
      if state.get() > 0 then state.set(state.get() - 1) else ???

    def put(e: Put)(using ProcessorTaskContext[InventoryReqs, Nothing]): Unit =
      if ShoppingCartConfig.LOGGING then ctx.log.info(s"Putting ${e.item} in inventory")
      state.set(state.get() + 1)

    def get_req(e: Get)(using ReplierTaskContext[InventoryReqs, Nothing, InventoryReqs, InventoryReps]): Unit =
      if ShoppingCartConfig.LOGGING then ctx.log.info(s"Checking if ${e.item} is in inventory")
      if state.get() > 0 then
        reply(GetReply(e.item, true))
        state.set(state.get() - 1)
      else reply(GetReply(e.item, false))

    def put_req(e: Put)(using ReplierTaskContext[InventoryReqs, Nothing, InventoryReqs, InventoryReps]): Unit =
      if ShoppingCartConfig.LOGGING then ctx.log.info(s"Putting ${e.item} in inventory")
      state.set(state.get() + 1)
      reply(PutReply(e.item, true))

    override def onNext(using ProcessorTaskContext[InventoryReqs, Nothing])(event: InventoryReqs): Unit = event match
      case e: Get => get(e)
      case e: Put => put(e)

    override def onAsk(using ctx: ReplierTaskContext[InventoryReqs, Nothing, InventoryReqs, InventoryReps])(
        event: InventoryReqs
    ): Unit = event match
      case e: Get => get_req(e)
      case e: Put => put_req(e)
  end InventoryTask // class

  @experimental
  class OrdersTask extends CustomProcessorTask[OrderOps, Nothing]:
    import portals.DSL.*

    override def onNext(using ProcessorTaskContext[InventoryReqs, Nothing])(event: OrderOps): Unit =
      if ShoppingCartConfig.LOGGING then ctx.log.info(s"Ordering $event")
  end OrdersTask // class
