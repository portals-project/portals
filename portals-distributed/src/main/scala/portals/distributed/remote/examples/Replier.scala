package portals.distributed.remote.examples

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.distributed.remote.*
import portals.distributed.remote.RemoteExtensions.*
import portals.distributed.server.*

object Replier extends SubmittableApplication:
  def apply(): Application =
    PortalsApp("Replier"):
      val portal = Portal[String, String]("reverse")
      val workflow = Workflows[Nothing, Nothing]()
        .source(Generators.empty[Nothing].stream)
        .replier[Nothing](portal)(_ => ???): //
          x => //
            log.info(x)
            reply(x.reverse)
        .sink()
        .freeze()

      // val generator = Generators.fromList(List("HelloWorld", "Sophia", "Jonas"))
      // val wf = Workflows[String, String]()
      //   .source(generator.stream)
      //   .logger()
      //   .sink()
      //   .freeze()

object ReplierServer extends App:
  val port = "8081"
  Client.port = port
  RemoteSBTRunServer.main(Array(port))

  Client.launchObject(Replier)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)
