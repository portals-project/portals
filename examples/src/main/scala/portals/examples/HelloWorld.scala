package portals.examples

import portals.api.builder.ApplicationBuilder
import portals.application.Application
import portals.Systems

/** Hello World
  *
  * This example creates a workflow that prints all the ingested events to the
  * logger. We submit the event containing the message "Hello, World!" and
  * expect it to be printed.
  */
object HelloWorld:
  import portals.api.dsl.DSL.*

  val app: Application = {
    val builder = ApplicationBuilder("app")

    val message = "Hello, World!"
    val generator = builder.generators.fromList(List(message))

    val _ = builder
      .workflows[String, String]("hello")
      .source(generator.stream)
      .map { x => x }
      // .logger()
      .sink()
      .freeze()

    val application = builder.build()

    application
  }

@main def HelloWorldMain(): Unit =
  val application = HelloWorld.app
  // ASTPrinter.println(application) // print the application AST
  val system = Systems.test()
  system.launch(application)
  system.stepUntilComplete()
  system.shutdown()
