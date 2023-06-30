package portals.examples.shoppingcart.tasks

import portals.api.dsl.DSL.*
import portals.application.*
import portals.application.task.*
import portals.application.task.MapTaskStateExtension.*
import portals.examples.shoppingcart.ShoppingCartConfig
import portals.examples.shoppingcart.ShoppingCartEvents.*

object AnalyticsTask:
  type I = (Item, Count)
  type O = (List[(Item)]) // Top100
  type Req = AnalyticsReqs
  type Res = AnalyticsReps
  type Context = ProcessorTaskContext[I, O]
  type RepContext = ReplierTaskContext[I, O, Req, Res]
  type PortalRef = AtomicPortalRefKind[Req, Res]
  type Task = GenericTask[I, O, Req, Res]

  private final val top100: PerTaskState[Map[Item, Int]] =
    PerTaskState[Map[Item, Int]]("state", Map.empty)

  private final val max: PerTaskState[Int] =
    PerTaskState[Int]("max", 0)

  private final val min: PerTaskState[Int] =
    PerTaskState[Int]("min", 0)

  private def setMaxMin()(using Context): Unit =
    val t100 = top100.get()
    max.set(t100.values.max)
    min.set(t100.values.min)

  private def pruneTop100()(using Context): Unit =
    val t100 = top100.get()
    if t100.size >= 100 then
      val minItemCount = t100.minBy(_._2)
      top100.remove(minItemCount._1)

  private def onNext(event: (Item, Count))(using Context): Unit =
    event match
      case (item, count) if count < min.get() =>
        if ShoppingCartConfig.LOGGING then ctx.log.info(s"Item $item with count $count is not added to top100")
      case (item, count) =>
        if top100.get().contains(item) then
          if ShoppingCartConfig.LOGGING then ctx.log.info(s"Item $item with count $count is updated in the top100")
          top100.update(item, count)
        else
          if ShoppingCartConfig.LOGGING then ctx.log.info(s"Item $item with count $count is added to the top100")
          top100.update(item, count)
          pruneTop100()
          setMaxMin()
          ctx.emit(top100.get().keys.toList)

  private def onAsk(event: Req)(using RepContext): Unit =
    val t100 = top100.get().keys.toList
    reply(Top100Reply(t100))

  def apply(portal: PortalRef): Task =
    Tasks.replier(portal: PortalRef)(onNext)(onAsk)
