package portals.distributed.remote

import portals.application.Application
import portals.distributed.remote.RemoteClient.*
import portals.distributed.remote.RemoteShared.*
import portals.runtime.BatchedEvents.*
import portals.system.PortalsSystem

class RemoteSystem extends PortalsSystem:
  private val runtime: RemoteRuntime = new RemoteRuntime()
  private var _remotes: List[List[EventBatch]] = List.empty
  var url: String = ""

  def launch(application: Application): Unit = runtime.launch(application)

  def step(): Unit =
    val remotes = runtime.step()
    if remotes != Nil then
      _remotes = remotes :: _remotes
      postSteps()

  def canStep(): Boolean = runtime.canStep()

  def stepUntilComplete(): Unit =
    while canStep() do step()

  def stepUntilComplete(max: Int): Unit =
    var i = 0
    while canStep() && i < max do
      step()
      i += 1

  def shutdown(): Unit = runtime.shutdown()

  def postSteps(): Unit =
    if _remotes != Nil then
      // println(_remotes.reverse)
      _remotes.reverse.foreach { batches =>
        batches.foreach { batch =>
          batch match
            case AskBatch(meta, list) =>
              val _url = GET_URL(batch)
              // A_1
              val b = TRANSFORM_ASK_1(batch, url)
              val e = PortalRequest(List(b.asInstanceOf[AskBatch[String]]))
              postToPortalReq(_url, e)
            case ReplyBatch(meta, list) =>
              val url = GET_URL(batch)
              // R_1
              val b = TRANSFORM_REP_1(batch)
              val e = PortalResponse(List(b.asInstanceOf[ReplyBatch[String]]))
              postToPortalRes(url, e)
            case _ => ???
        }
      }
      _remotes = List.empty

  def feed(batch: List[EventBatch]): Unit = runtime.feed(batch)
