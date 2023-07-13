package portals.distributed.remote.examples

import scala.util.*

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.distributed.remote.*
import portals.distributed.remote.RemoteExtensions.*
import portals.distributed.server.*

object Requester extends SubmittableApplication:
  def apply(): Application =
    PortalsApp("Requester"):

      val remotePortal = RemoteRegistry.portals
        .get[String, String](
          "http://localhost:8081",
          "Replier/portals/reverse",
        )

      val generator = Generators.fromList(List("HelloWorld", "Sophia", "Jonas"))

      val workflow = Workflows[String, String]()
        .source(generator.stream)
        .logger()
        .asker(remotePortal): //
          x =>
            ask(remotePortal)(x)
              .onComplete {
                case Success(value) => ctx.emit(value)
                case Failure(exception) => ctx.emit(exception.getMessage)
              }
        .logger()
        .sink()
        .freeze()

object RequesterClient extends App:
  val port = "8082"
  Client.port = port
  RemoteSBTRunServer.main(Array(port))

  Client.launchObject(Requester)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)
