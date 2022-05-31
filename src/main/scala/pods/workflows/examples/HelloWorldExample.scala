package pods.workflows.examples

/** HelloWorld Example
  *
  * This is a collection of canonical hello world examples.
  */

/** Logged Hello World
  *
  * This example creates a workflow that prints all the ingested events to the
  * logger. We submit the event containing the message "Hello, World!" and 
  * expect it to be printed.
  */
@main def LoggedHelloWorld() =
  import pods.workflows.*
  
  val builder = Workflows
    .builder()
    .withName("wf")

  val flow = builder
    .source[String]()
    .withName("input")
    .withLogger() // print the output to logger
    .sink()
    .withName("output")

  val wf = builder.build()

  val system = Systems.local()
  system.launch(wf)

  val helloWorld = "Hello, World!"

  val iref: IStreamRef[String] = system.registry("wf/input").resolve()

  iref.submit(helloWorld)
  iref.fuse()

  system.shutdown()
    

/** Explicit Logged Hello World
  *
  * This example prints the ingested message "Hello, World!" to the logger by
  * providing a custom Processor TaskBehavior. The custom behavior prints any
  * events to log, and then emits these events.
  */
@main def OutputHelloWorld() =
  import pods.workflows.*
  
  val builder = Workflows
    .builder()
    .withName("wf")

  val flow = builder
    .source[String]()
    .withName("input")
    .processor { ctx ?=> event =>
      ctx.log.info("Hello, World!")
      ctx.emit(event)
    }
    .sink()
    .withName("output")

  val wf = builder.build()

  val system = Systems.local()
  system.launch(wf)

  val helloWorld = "Hello, World!"

  val iref = system.registry[String]("wf/input").resolve()

  iref.submit(helloWorld)
  iref.fuse()

  system.shutdown()
  