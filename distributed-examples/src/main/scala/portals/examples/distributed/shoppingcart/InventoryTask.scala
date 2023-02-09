package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*

import ShoppingCartEvents.*

@experimental
class InventoryTask(portal: AtomicPortalRefKind[InventoryReqs, InventoryReps])
    extends CustomReplierTask[InventoryReqs, Nothing, InventoryReqs, InventoryReps]:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  lazy val state: (Context | ContextReply) ?=> PerKeyState[Int] = PerKeyState[Int]("state", 0)

  def get(e: Get)(using Context): Unit =
    ctx.log.info(s"Taking $e.item from inventory")
    if state.get() > 0 then state.set(state.get() - 1) else ???

  def put(e: Put)(using Context): Unit =
    ctx.log.info(s"Putting $e.item in inventory")
    state.set(state.get() + 1)

  def get_req(e: Get)(using ContextReply): Unit =
    ctx.log.info(s"Checking if $e.item is in inventory")
    if state.get() > 0 then
      reply(GetReply(e.item, true))
      state.set(state.get() - 1)
    else reply(GetReply(e.item, false))

  def put_req(e: Put)(using ContextReply): Unit =
    ctx.log.info(s"Putting $e.item in inventory")
    state.set(state.get() + 1)
    reply(PutReply(e.item, true))

  override def onNext(using Context)(event: InventoryReqs): Unit = event match
    case e: Get => get(e)
    case e: Put => put(e)

  override def onAsk(using ctx: ContextReply)(event: InventoryReqs): Unit = event match
    case e: Get => get_req(e)
    case e: Put => put_req(e)
