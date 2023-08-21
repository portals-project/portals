package portals.distributed.remote.examples

import scala.util.Failure
import scala.util.Success

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.distributed.*
import portals.distributed.remote.*
import portals.distributed.remote.RemoteExtensions.*
import portals.examples.shoppingcart.ThrottledGenerator
import portals.system.Systems

object Replier extends SubmittableApplication:
  def apply(): Application =
    PortalsApp("Replier"):
      val portal = Portal[String, String]("reverse")
      val workflow = Workflows[Nothing, Nothing]("asdf")
        .source(Generators.empty[Nothing].stream)
        .replier[Nothing](portal)(_ => ???): //
          x => //
            log.info(x)
            reply(x.reverse)
        .sink()
        .freeze()

object ReplierServer extends App:
  val host = "localhost"
  val port = "8081"

  RemoteSBTRunServer.InternalRemoteSBTRunServer.main(Array(host, port))

  Client.launchObject(Replier, host, port.toInt)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)
