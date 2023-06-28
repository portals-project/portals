package portals.distributed

import cask.main.Main

/** An alternative server that can run with `sbt run` without stopping. */
object SBTRunServer extends cask.Main:
  // define the routes which this server handles
  val allRoutes = Seq(Server)

  // execute the main method of this server
  this.main(args = Array.empty)

  // sleep so that we don't exit prematurely
  Thread.sleep(Long.MaxValue)

  // exit before running this servers main method (again)
  System.exit(0)
