package portals.distributed.remote.examples

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.*
import portals.distributed.remote.*
import portals.distributed.remote.RemoteExtensions.*
import portals.distributed.server.*
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

      // val generator = Generators.fromList(List("HelloWorld", "Sophia", "Jonas"))
      // val wf = Workflows[String, String]()
      //   .source(generator.stream)
      //   .logger()
      //   .sink()
      //   .freeze()

object ReplierServer extends App:
  val port = "8081"
  Client.port = port
  RemoteServerRuntime.system.url = s"http://localhost:$port"
  RemoteSBTRunServer.main(Array(port))

  Client.launchObject(Replier)
  // Client.launchObject(Requester)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)

object XYZ extends App:
  ASTPrinter.println(Replier())
