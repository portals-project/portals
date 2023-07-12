package portals.distributed.examples

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.application.task.*
import portals.distributed.server.SBTRunServer
import portals.distributed.server.Server
import portals.system.Systems
import portals.util.Future
import upickle.default.*

object RemoteServer extends cask.MainRoutes:
  import RemoteShared.*

  @cask.post("/remote")
  def remote(request: cask.Request) =
    println(request)
    val bytes = request.readAllBytes()
    val event = read[PortalRequest](bytes)
    println(event)
    val response = event match
      case PortalRequest(url, path, event) =>
        val resp = PortalResponse(event.reverse)
        val bytes = write(resp).getBytes()
        bytes
    cask.Response(response, statusCode = 200)

  initialize()