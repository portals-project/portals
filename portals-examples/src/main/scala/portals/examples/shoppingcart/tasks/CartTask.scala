package portals.examples.shoppingcart.tasks

import scala.annotation.experimental

import portals.api.dsl.DSL.*
import portals.application.task.*
import portals.application.AtomicPortalRef
import portals.application.AtomicPortalRefKind
import portals.examples.shoppingcart.*
import portals.examples.shoppingcart.ShoppingCartEvents.*

object CartTask:
  type I = CartOps
  type O = OrderOps
  type Req = InventoryReqs
  type Res = InventoryReps
  type Context = AskerTaskContext[I, O, Req, Res]
  type PortalRef = AtomicPortalRefKind[Req, Res]
  type Task = GenericTask[I, O, Req, Res]

  private final val state: PerKeyState[CartState] =
    PerKeyState[CartState]("state", CartState.zero)

  private def addToCart(event: AddToCart, portal: PortalRef)(using Context): Unit =
    val req = Get(event.item)
    val resp = ask(portal)(req)
    Await(resp):
      resp.value match
        case Some(GetReply(item, true)) =>
          if ShoppingCartConfig.LOGGING then ctx.log.info(s"User ${event.user} added $item to cart")
          state.set(state.get().add(item))
        case Some(GetReply(item, false)) =>
          if ShoppingCartConfig.LOGGING then
            ctx.log.info(s"User ${event.user} tried to add $item to cart, but it was not in inventory")
        case _ =>
          if ShoppingCartConfig.LOGGING then ctx.log.info("Unexpected response")
          ???

  private def removeFromCart(event: RemoveFromCart, portal: PortalRef)(using Context): Unit =
    if ShoppingCartConfig.LOGGING then ctx.log.info(s"User ${event.user} removed ${event.item} from cart")
    val req = Put(event.item)
    val _ = ask(portal)(req)

  private def checkout(event: Checkout)(using Context): Unit =
    if ShoppingCartConfig.LOGGING then ctx.log.info(s"Checking out ${event.user}")
    val cart = state.get()
    ctx.emit(Order(event.user, cart))
    state.del()

  private def onNext(portal: PortalRef)(event: CartOps)(using Context): Unit =
    event match
      case event: AddToCart => addToCart(event, portal)
      case event: RemoveFromCart => removeFromCart(event, portal)
      case event: Checkout => checkout(event)

  def apply(portal: PortalRef): Task =
    Tasks.asker(portal)(onNext(portal))

end CartTask
