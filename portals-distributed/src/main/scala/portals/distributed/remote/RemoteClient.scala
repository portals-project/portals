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

object RemoteClient:
  import RemoteShared.*

  /** Post the Launch `event` to the server. */
  def postToPortal(event: PortalRequest): String =
    val bytes = write(event).getBytes()
    val response = requests.post("http://localhost:8080/remote", data = bytes)
    response match
      case r if r.statusCode == 200 =>
        val resp = read[PortalResponse](r.bytes)
        resp.event
      case r => ???
