// package portals.benchmark.benchmarks

// import portals.api.builder.*
// import portals.api.dsl.DSL.*
// import portals.application.task.TaskStates
// import portals.benchmark.*
// import portals.benchmark.systems.*
// import portals.benchmark.BenchmarkUtils.*
// import portals.system.Systems

// object FibonacciBenchmark extends Benchmark:
//   private val config = BenchmarkConfig()
//   config.set("--nFib", 10) // number of events

//   override val name = "FibonacciBenchmark"

//   override def initialize(args: List[String]): Unit =
//     config.parseArgs(args)

//   override def cleanupOneIteration(): Unit = ()

//   override def runOneIteration(): Unit =
//     val nFib = config.getInt("--nFib")

//     sealed trait FibEvent:
//       def receiver: Int
//     case class FibRequest(sender: Int, receiver: Int) extends FibEvent
//     case class FibResponse(sender: Int, receiver: Int, v: Long) extends FibEvent

//     val system = Systems.local()

//     val completer = CompletionWatcher()

//     val builder = ApplicationBuilder("app")

//     val generator = builder.generators.fromList[FibEvent](List(FibRequest(sender = -1, receiver = nFib)))
//     val sequencer = builder.sequencers.random[FibEvent]()
//     val _ = builder.connections.connect(generator.stream, sequencer)

//     val workflow = builder
//       .workflows[FibEvent, FibEvent]("fibonacci")
//       .source(sequencer.stream)
//       .key { _.receiver }
//       // memoizing fib
//       .init {
//         val fib0 = TaskStates.perKey[Long]("fib0", -1)
//         val fib1 = TaskStates.perKey[Long]("fib1", -1)
//         val fib2 = TaskStates.perKey[Long]("fib2", -1)
//         val requests = TaskStates.perKey[List[Int]]("requests", List.empty)
//         TaskBuilder.processor {
//           case FibRequest(sender, receiver) =>
//             if receiver <= 0 then ctx.emit(FibResponse(receiver, sender, v = 0))
//             else if receiver == 1 then ctx.emit(FibResponse(receiver, sender, v = 1))
//             else if requests.get() == List.empty then
//               requests.set(List(sender))
//               ctx.emit(FibRequest(receiver, receiver - 1))
//               ctx.emit(FibRequest(receiver, receiver - 2))
//             else requests.set(sender :: requests.get())
//           case FibResponse(sender, receiver, v) =>
//             if sender == receiver - 2 then fib2.set(v)
//             else if sender == receiver - 1 then fib1.set(v)
//             else
//               // ctx.log.info("results: {} {}", receiver, v)
//               completer.complete()
//             if fib1.get() >= 0 && fib2.get() >= 0 then
//               fib0.set(fib1.get() + fib2.get())
//               requests.get().foreach { x => ctx.emit(FibResponse(receiver, x, v = fib0.get())) }
//               requests.set(List.empty)
//         }
//       }
//       .sink()
//       .freeze()

//     val _ = builder.connections.connect(workflow.stream, sequencer)

//     val application = builder.build()

//     system.launch(application)

//     completer.waitForCompletion()

//     system.shutdown()
