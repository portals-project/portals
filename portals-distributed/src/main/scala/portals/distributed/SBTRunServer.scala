package portals.distributed

import portals.distributed.Server

import cask.main.Main
import io.undertow.Undertow

/** An alternative server that can run with `sbt run` without stopping.
  *
  * @example
  *   {{{
  * sbt "distributed/runMain portals.distributed.SBTRunServer localhost 8080"
  *   }}}
  */
object SBTRunServer:
  object InternalSBTRunServer extends cask.Main:
    // define the routes which this server handles
    override val allRoutes = Seq(Server)

    // override the default main method to handle the port argument
    override def main(args: Array[String]): Unit = {
      val host = if args.length > 0 then Some(args(0).toString) else Some("localhost")
      val port = if args.length > 1 then Some(args(1).toInt) else Some(8080)
      if (!verbose) Main.silenceJboss()
      val server = Undertow.builder
        .addHttpListener(port.get, host.get)
        .setHandler(defaultHandler)
        .build
      server.start()
    }

  // using main method here instead of extending App, see:
  // https://users.scala-lang.org/t/args-is-null-while-extending-app-even-when-runtime-args-are-provided/8564
  def main(args: Array[String]): Unit =
    // execute the main method of the server, starting it
    InternalSBTRunServer.main(args)

    // sleep so we don't exit prematurely
    Thread.sleep(Long.MaxValue)

    // exit the application (not sure if necessary here)
    System.exit(0)
