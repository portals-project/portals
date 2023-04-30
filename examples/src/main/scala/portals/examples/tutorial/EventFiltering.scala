package portals.examples.tutorial

import portals.api.builder.ApplicationBuilder
import portals.application.Application
import portals.system.Systems

/** Event Filtering
  *
  * This example creates a workflow that filters out events containing the
  * string "secret" and sends them to a separate sink. The remaining events are
  * sent to the default logger sink.
  */
object EventFiltering:
  import portals.api.dsl.DSL.*

  val app: Application = {
    val builder = ApplicationBuilder("app")

    val messages = List("Hello, World!", "This is a secret message", "Goodbye!")

    val generator = builder.generators.fromList(messages)

    val _ = builder
      .workflows[String, String]("workflow")
      .source(generator.stream)
      .filter { !_.contains("secret") }
      .logger()
      .sink()
      .freeze()

    builder.build()
  }

@main def EventFilteringMain(): Unit =
  val system = Systems.interpreter()
  system.launch(EventFiltering.app)
  system.stepUntilComplete()
  system.shutdown()
  println("EventFilteringMain: done")
