package portals.distributed.examples

import portals.api.dsl.DSL.*
import portals.application.Application
import portals.distributed.SubmittableApplication
import portals.system.Systems

/** A simple application that prints "Hello World!" to the log.
  *
  * Run this application either locally (see the main method, example below), or
  * submit it to a distributed/remote execution (see examples below).
  *
  * Note: it is important to use the correct Java path when submitting the apps
  * (typically ending with a $ symbol).
  *
  * @example
  *   Run it locally
  *   {{{
  * sbt "distributed/runMain portals.distributed.examples.HelloWorld"
  *   }}}
  *
  * @example
  *   Submit it to a server
  *   {{{
  * // start the server (in a different terminal)
  * sbt "distributed/runMain portals.distributed.SBTRunServer"
  * sbt "distributed/runMain portals.distributed.ClientCLI submitDir --directory portals-distributed/target/scala-3.3.0/classes"
  * sbt "distributed/runMain portals.distributed.ClientCLI launch --application portals.distributed.examples.HelloWorld$"
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

  def main(args: Array[String]): Unit =
    val system = Systems.test()
    system.launch(HelloWorld.apply())
    system.stepUntilComplete()
    system.shutdown()
