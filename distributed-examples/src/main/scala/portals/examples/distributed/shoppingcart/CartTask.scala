package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*

import ShoppingCartEvents.*

@experimental
class CartTask(portal: AtomicPortalRefKind[InventoryReqs, InventoryReps])
    extends CustomAskerTask[CartOps, OrderOps, InventoryReqs, InventoryReps]:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  lazy val state: Context ?=> PerKeyState[CartState] =
    PerKeyState[CartState]("state", CartState.zero)

  // format: off
  private def addToCart(event: AddToCart)(using Context): Unit =
    val resp = portal.ask(Get(event.item))
    resp.await { resp.value.get match
      case GetReply(item, success) =>
        if success then
          state.set(state.get().add(item))
          ctx.log.info(s"User $event.user added $item to cart")
        else 
          ctx.log.info(s"User $event.user tried to add $item to cart, but it was not in inventory")
      case _ => 
        ctx.log.info("Unexpected response")
        ???
    }
  // format: on

  private def removeFromCart(event: RemoveFromCart)(using Context): Unit =
    portal.ask(Put(event.item))
    ctx.log.info(s"User $event.user removed $event.item from cart")

  private def checkout(event: Checkout)(using Context): Unit =
    val cart = state.get()
    ctx.emit(Order(event.user, cart))
    state.del()
    ctx.log.info(s"Checking out $event.user with cart $cart")

  override def onNext(using Context)(event: CartOps): Unit = event match
    case event: AddToCart => addToCart(event)
    case event: RemoveFromCart => removeFromCart(event)
    case event: Checkout => checkout(event)
