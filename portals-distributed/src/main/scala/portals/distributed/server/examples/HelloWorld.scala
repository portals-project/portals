package portals.distributed.server.examples

import portals.api.dsl.DSL.*
import portals.application.Application
import portals.distributed.server.SubmittableApplication

/** HelloWorld example for submitting an application to the Portals Server.
  *
  * Note: it is important to use the correct Java path (typically ending with a
  * $ symbol).
  *
  * @example
  *   {{{
  * // comment out this whole file (so that it doesn't compile with the server.)
  *
  * // start the server (in a different terminal)
  * sbt "distributed/runMain portals.distributed.server.SBTRunServer"
  *
  * // uncomment this whole file so that we can submit the app
  *
  * // submit the class files with the client
  * sbt "distributed/runMain portals.distributed.server.ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes"
  *
  * // launch the application with the client
  * sbt "distributed/runMain portals.distributed.server.ClientCLI launch --application portals.distributed.server.examples.HelloWorld$"
  *   }}}
  */
object HelloWorld extends SubmittableApplication:
  override def apply(): Application =
    // simple hello world example that prints Hello World! in reverse
    PortalsApp("HelloWorld") {
      val generator = Generators.signal("Hello World!")
      val workflow = Workflows[String, String]()
        .source(generator.stream)
        .map(_.reverse)
        .logger()
        .sink()
        .freeze()
    }
