package portals.distributed.remote

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

import cask.main.Main
import io.undertow.Undertow
import upickle.default.*

object RemoteSBTRunServer extends cask.Main:
  // define the routes which this server handles
  val allRoutes = Seq(RemoteServer)

  override def main(args: Array[String]): Unit = {
    val port = if args.length > 0 then Some(args(0).toInt) else Some(8080)
    if (!verbose) Main.silenceJboss()
    val server = Undertow.builder
      .addHttpListener(port.get, host)
      .setHandler(defaultHandler)
      .build
    server.start()
  }

@main def run(port: String) =
  // Execute the main method of this server
  RemoteSBTRunServer.main(Array(port))

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)
