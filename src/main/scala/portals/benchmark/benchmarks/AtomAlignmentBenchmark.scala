package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object AtomAlignmentBenchmark extends Benchmark:

  private val config = BenchmarkConfig()
  config.set("--nEvents", 1024 * 1024) // number of events
  config.set("--stepSize", 1024) // atom size

  override val name = "AtomAlignmentBenchmark"

  override def initialize(args: List[String]): Unit = config.parseArgs(args)

  override def cleanupOneIteration(): Unit = () // do nothing

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val stepSize = config.getInt("--stepSize")

    val completer = CompletionWatcher()

    val system = Systems.asyncLocal()

    val builder = ApplicationBuilders.application("runOneIteration")

    // generator
    val generator = builder.generators.fromRange(0, nEvents, stepSize)

    // create grid
    val forwarder = Tasks.flatMap[Int, Int](x => List(x))
    val silent = Tasks.flatMap[Int, Int](x => List.empty[Int])

    val wfb = builder.workflows[Int, Int]("workflow")

    val source = wfb.source(generator.stream)
    val p11 = source.task(forwarder)
    val p12 = source.task(silent)
    val p13 = source.task(silent)
    // TODO: change here so we can use wfb instead of `source`, or other options
    val p21 = source.from(p11, p12, p13)(forwarder)
    val p22 = source.from(p11, p12, p13)(silent)
    val p23 = source.from(p11, p12, p13)(silent)
    val compl = source.from(p21, p22, p23)(completer.task { _ == nEvents - 1 })
    val sink = compl.sink()
    val workflow = sink.freeze()
    val app = builder.build()

    system.launch(app)

    // TODO: should trigger shutdown even if Await is timed out
    completer.waitForCompletion()

    system.shutdown()

object AtomAlignmentWithoutAlignmentBenchmark extends Benchmark:

  private val config = BenchmarkConfig()
  config.set("--nEvents", 1024 * 1024) // number of events
  config.set("--stepSize", 1024) // atom size

  override val name = "AtomAlignmentWithoutAlignmentBenchmark"

  override def initialize(args: List[String]): Unit = config.parseArgs(args)

  override def cleanupOneIteration(): Unit = () // do nothing

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val stepSize = config.getInt("--stepSize")

    val completer = CompletionWatcher()

    val system = Systems.asyncLocalWOA()

    val builder = ApplicationBuilders.application("runOneIteration")

    // generator
    val generator = builder.generators.fromRange(0, nEvents, stepSize)

    // create grid
    val forwarder = Tasks.flatMap[Int, Int](x => List(x))
    val silent = Tasks.flatMap[Int, Int](x => List.empty[Int])

    val wfb = builder.workflows[Int, Int]("workflow")

    val source = wfb.source(generator.stream)
    val p11 = source.task(forwarder)
    val p12 = source.task(silent)
    val p13 = source.task(silent)
    // TODO: change here so we can use wfb instead of `source`, or other options
    val p21 = source.from(p11, p12, p13)(forwarder)
    val p22 = source.from(p11, p12, p13)(silent)
    val p23 = source.from(p11, p12, p13)(silent)
    val compl = source.from(p21, p22, p23)(completer.task { _ == nEvents - 1 })
    val sink = compl.sink()
    val workflow = sink.freeze()
    val app = builder.build()

    system.launch(app)

    // TODO: should trigger shutdown even if Await is timed out
    completer.waitForCompletion()

    system.shutdown()
