package portals.distributed

import cask.main.Main
import mainargs.arg
import mainargs.main
import mainargs.ParserForClass

/** An alternative server that can run with `sbt run` without stopping. */
object SBTRunServer extends cask.Main:

  // TODO: read port from commandline
  // example: sbt "distributed/runMain portals.distributed.SBTRunServer --port 8080"
  override val port: Int = 8080

  // define the routes which this server handles
  val allRoutes = Seq(Server)

  // execute the main method of this server
  this.main(args = Array.empty)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)
