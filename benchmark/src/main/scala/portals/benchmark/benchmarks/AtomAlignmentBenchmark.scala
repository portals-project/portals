package portals.benchmark.benchmarks

import portals.api.builder.*
import portals.api.builder.TaskExtensions.*
import portals.api.dsl.DSL.*
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.system.Systems
import portals.system.TestSystem

object AtomAlignmentBenchmark extends Benchmark:

  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nAtomSize") // 128
    .setRequired("--sSystem") // "async"
    .set("--withWork", false)

  override val name = "AtomAlignmentBenchmark"

  override def initialize(args: List[String]): Unit = config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nAtomSize = config.getInt("--nAtomSize")
    val sSystem = config.get("--sSystem")
    val withWork = config.get("--withWork").toBoolean

    val completer = CompletionWatcher()

    val system = sSystem match
      case "parallel" => Systems.parallel(8)
      case "test" => Systems.test()
      case _ => ???

    val builder = ApplicationBuilder("runOneIteration")

    // generator
    val generator = builder.generators.fromRange(0, nEvents, nAtomSize)

    // create grid
    val forwarder =
      if withWork then
        TaskBuilder.flatMap[Int, Int](x =>
          Computation(1024 * 2)
          List(x)
        )
      else TaskBuilder.flatMap[Int, Int](x => List(x))

    val silent = TaskBuilder.flatMap[Int, Int](x => List.empty[Int])

    val wfb = builder.workflows[Int, Int]("workflow")

    val source = wfb.source(generator.stream)
    val p11 = source.task(forwarder)
    val p12 = source.task(silent)
    val p13 = source.task(silent)
    // TODO: change here so we can use wfb instead of `source`, or other options
    val p21 = source.combineAllFrom(p11, p12, p13)(forwarder)
    val p22 = source.combineAllFrom(p11, p12, p13)(silent)
    val p23 = source.combineAllFrom(p11, p12, p13)(silent)
    val compl = source.combineAllFrom(p21, p22, p23)(completer.task { _ == nEvents - 1 })
    val sink = compl.sink()
    val workflow = sink.freeze()
    val app = builder.build()

    system.launch(app)

    if sSystem == "sync" then system.asInstanceOf[TestSystem].stepUntilComplete()

    // TODO: should trigger shutdown even if Await is timed out
    completer.waitForCompletion()

    system.shutdown()
