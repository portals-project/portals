package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object CountingActorBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
  config.set("--nEvents", 1024 * 1024) // number of events
  config.set("--nAtomSize", 128) // atomSize

  override val name = "CountingActorBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nAtomSize = config.getInt("--nAtomSize")

    val completer = CompletionWatcher()

    val system = Systems.asyncLocal()

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

    completer.waitForCompletion()

    system.shutdown()
