package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.BenchmarkUtils.*
import portals.system.async.DataParallel
import portals.DSL.*

object DataParallelThroughputBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
    .set("--nEvents", 1024 * 1024) // number of events
    .set("--nPartitions", 128) // k, number of workflows
    .set("--nAtomSize", 128) // atomSize

  override val name = "DataParallelThroughputBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nPartitions = config.getInt("--nPartitions")
    val nAtomSize = config.getInt("--nAtomSize")
    val nEventsPerPartition = nEvents / nPartitions

    val completer = CountingCompletionWatcher(nPartitions)

    val builder = ApplicationBuilders.application("app")

    val generator = builder.generators.generator[Int](DataParallel.fromRange(0, nEvents, nAtomSize))

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .map { x => { if x > nEvents - nPartitions - 1 then completer.complete(); x } }
      .sink()
      .freeze()

    val application = builder.build()

    val system = Systems.dataParallel(nPartitions)

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()
