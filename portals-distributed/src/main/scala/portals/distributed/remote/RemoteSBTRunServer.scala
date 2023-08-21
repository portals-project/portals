package portals.distributed.remote

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.application.task.*
import portals.distributed.SBTRunServer
import portals.distributed.Server
import portals.system.Systems
import portals.util.Future

import cask.main.Main
import io.undertow.Undertow
import upickle.default.*

object RemoteSBTRunServer:

  object InternalRemoteSBTRunServer extends cask.Main:
    // define the routes which this server handles
    val allRoutes = Seq(RemoteServer)

    // override the default main method to handle the port argument
    override def main(args: Array[String]): Unit = {
      val host = if args.length > 0 then Some(args(0).toString) else Some("localhost")
      val port = if args.length > 1 then Some(args(1).toInt) else Some(8080)

      // hack
      RemoteServerRuntime.system.url = s"http://${host.get}:${port.get}"

      if (!verbose) Main.silenceJboss()
      val server = Undertow.builder
        .addHttpListener(port.get, host.get)
        .setHandler(defaultHandler)
        .build
      server.start()
    }

  def main(args: Array[String]): Unit =
    // execute the main method of the server, starting it
    InternalRemoteSBTRunServer.main(args)

    // sleep so we don't exit prematurely
    Thread.sleep(Long.MaxValue)

    // exit the application (not sure if necessary here)
    System.exit(0)
