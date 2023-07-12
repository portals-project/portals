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

object RemoteShared:
  sealed case class PortalRequest(url: String, path: String, event: String) derives ReadWriter
  sealed case class PortalResponse(event: String) derives ReadWriter