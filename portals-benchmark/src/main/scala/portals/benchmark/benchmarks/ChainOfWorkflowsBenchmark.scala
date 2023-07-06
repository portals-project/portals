// package portals.benchmark.benchmarks

// import portals.api.builder.ApplicationBuilder
// import portals.api.dsl.DSL.*
// import portals.application.AtomicStreamRef
// import portals.application.Workflow
// import portals.benchmark.*
// import portals.benchmark.systems.*
// import portals.benchmark.BenchmarkUtils.*
// import portals.system.Systems

// object ChainOfWorkflowsBenchmark extends Benchmark:
//   private val config = BenchmarkConfig()
//   config.set("--nEvents", 1024 * 1024) // number of events
//   config.set("--stepSize", 128) // atom size
//   config.set("--chainLength", 128) // chain length

//   override val name = "ChainOfWorkflowsBenchmark"

//   override def initialize(args: List[String]): Unit =
//     config.parseArgs(args)

//   override def cleanupOneIteration(): Unit = ()

//   override def runOneIteration(): Unit =
//     val nEvents = config.getInt("--nEvents")
//     val stepSize = config.getInt("--stepSize")
//     val chainLength = config.getInt("--chainLength")

//     val completer = CompletionWatcher()

//     val system = Systems.local()

//     val builder = ApplicationBuilder("app")

//     val generator = builder.generators.fromRange(0, nEvents, stepSize)

//     def workflowFactory(name: String, stream: AtomicStreamRef[Int]): Workflow[Int, Int] =
//       builder
//         .workflows[Int, Int](name)
//         .source[Int](stream)
//         .map(_ + 1)
//         .sink()
//         .freeze()

//     var prev: Workflow[Int, Int] = workflowFactory("wf0", generator.stream)
//     Range(1, chainLength).foreach { i =>
//       prev = workflowFactory("wf" + i, prev.stream)
//     }

//     // completer
//     completer.workflow(prev.stream, builder) { _ == nEvents - 1 }

//     val application = builder.build()

//     system.launch(application)

//     completer.waitForCompletion()

//     system.shutdown()