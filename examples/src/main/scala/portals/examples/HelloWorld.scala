package portals.examples

import portals.*

/** Hello World
  *
  * This example creates a workflow that prints all the ingested events to the
  * logger. We submit the event containing the message "Hello, World!" and
  * expect it to be printed.
  */
@main def HelloWorld(): Unit =
  import portals.DSL.*

  val builder = ApplicationBuilder("app")

  val message = "Hello, World!"
  val generator = builder.generators.fromList(List(message))

  val _ = builder
    .workflows[String, String]("hello")
    .source(generator.stream)
    .map { x => x }
    .logger()
    .sink()
    .freeze()

  val application = builder.build()

  // ASTPrinter.println(application) // print the application AST

  val system = Systems.test()

  system.launch(application)

  system.stepUntilComplete()
  system.shutdown()
