package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object CountingActorBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nAtomSize") // 128

  override val name = "CountingActorBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nAtomSize = config.getInt("--nAtomSize")
    val sSystem = config.get("--sSystem")

    val completer = CompletionWatcher()

    val system = sSystem match
      case "async" => Systems.asyncLocal()
      case "noGuarantees" => Systems.asyncLocalNoGuarantees()
      case "microBatching" => Systems.asyncLocalMicroBatching()
      case "sync" => Systems.syncLocal()
      case _ => ???

    val builder = ApplicationBuilders.application("app")

    val generator = builder.generators.fromRange(0, nEvents, nAtomSize)

    var workflow = builder
      .workflows[Int, Int]("workflow")
      .source[Int](generator.stream)
      .init {
        var state: Int = 0
        Tasks.map { x =>
          state += 1
          if state == nEvents - 1 then completer.complete(true)
          x
        }
      }
      .sink()
      .freeze()

    val application = builder.build()

    system.launch(application)

    if sSystem == "sync" then system.asInstanceOf[LocalSystemContext].stepAll()

    completer.waitForCompletion()

    system.shutdown()
