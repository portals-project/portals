package portals.examples.shoppingcart.tasks

import portals.api.dsl.DSL.*
import portals.application.task.*
import portals.application.AtomicPortalRef
import portals.application.AtomicPortalRefKind
import portals.examples.shoppingcart.ShoppingCartConfig
import portals.examples.shoppingcart.ShoppingCartEvents.*

object InventoryTask:
  type I = InventoryReqs
  type O = Nothing
  type Req = InventoryReqs
  type Res = InventoryReps
  type Context = ProcessorTaskContext[I, O]
  type RepContext = ReplierTaskContext[I, O, Req, Res]
  type PortalRef = AtomicPortalRefKind[Req, Res]
  type Task = GenericTask[I, O, Req, Res]

  private final val state: PerKeyState[Int] =
    PerKeyState[Int]("state", 0)

  private def get(e: Get)(using Context): Unit =
    if ShoppingCartConfig.LOGGING then ctx.log.info(s"Taking ${e.item} from inventory")
    state.get() match
      case x if x > 0 => state.set(x - 1)
      case _ => ???

  private def put(e: Put)(using Context): Unit =
    if ShoppingCartConfig.LOGGING then ctx.log.info(s"Putting ${e.item} in inventory")
    state.set(state.get() + 1)

  private def get_req(e: Get)(using RepContext): Unit =
    if ShoppingCartConfig.LOGGING then ctx.log.info(s"Checking if ${e.item} is in inventory")
    state.get() match
      case x if x > 0 =>
        reply(GetReply(e.item, true))
        state.set(x - 1)
      case _ =>
        reply(GetReply(e.item, false))

  private def put_req(e: Put)(using RepContext): Unit =
    if ShoppingCartConfig.LOGGING then ctx.log.info(s"Putting ${e.item} in inventory")
    state.set(state.get() + 1)
    reply(PutReply(e.item, true))

  private def onNext(event: InventoryReqs)(using Context): Unit = event match
    case e: Get => get(e)
    case e: Put => put(e)

  private def onAsk(event: InventoryReqs)(using RepContext): Unit = event match
    case e: Get => get_req(e)
    case e: Put => put_req(e)

  def apply(portal: PortalRef): Task =
    Tasks.replier(portal)(onNext)(onAsk)

end InventoryTask
