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

object RemoteSBTRunServer extends cask.Main:
  // define the routes which this server handles
  val allRoutes = Seq(RemoteServer)

  // execute the main method of this server
  this.main(args = Array.empty)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)
