package portals.benchmark.benchmarks

import portals.*
import portals.api.builder.ApplicationBuilder
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.api.dsl.DSL.*

object ChainOfTasksBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
  config.set("--nEvents", 1024 * 1024) // number of events
  config.set("--stepSize", 128) // atom size
  config.set("--chainLength", 128) // chain length

  override val name = "ChainOfTasksBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val stepSize = config.getInt("--stepSize")
    val chainLength = config.getInt("--chainLength")

    val completer = CompletionWatcher()

    val system = Systems.local()

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, nEvents, stepSize)

    var prev = builder
      .workflows[Int, Int]("workflow")
      .source[Int](generator.stream)

    Range(0, chainLength).foreach { i =>
      prev = prev.map { x => x }
    }

    prev
      .task { completer.task { _ == nEvents - 1 } }
      .sink()
      .freeze()

    val application = builder.build()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()
