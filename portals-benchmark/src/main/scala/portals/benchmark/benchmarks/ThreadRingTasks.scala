package portals.benchmark.benchmarks

import portals.api.builder.ApplicationBuilder
import portals.api.dsl.DSL.*
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.system.Systems
import portals.system.TestSystem

object ThreadRingTasks extends Benchmark:
  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nChainLength") // 128
    .setRequired("--sSystem") // "async"

  override val name = "ThreadRingTasks"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val chainLength = config.getInt("--nChainLength")
    val sSystem = config.get("--sSystem")

    val completer = CompletionWatcher()

    val system = sSystem match
      case "parallel" => Systems.parallel(1)
      case "test" => Systems.test()
      case _ => ???

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromList(List(nEvents))

    val sequencer = builder.sequencers.random[Int]()
    val _ = builder.connections.connect(generator.stream, sequencer)

    var prev = builder
      .workflows[Int, Int]("workflow")
      .source[Int](sequencer.stream)

    Range(0, chainLength).foreach { i =>
      prev = prev.map { x => if x == 0 then x else x - 1 }
    }

    val wf = prev
      .task { completer.task { _ == 0 } }
      .processor { x => if x > 0 then ctx.emit(x) } // consume end event so it terminates
      .sink()
      .freeze()

    // loop back
    val _ = builder.connections.connect(wf.stream, sequencer)

    val application = builder.build()

    system.launch(application)

    if sSystem == "sync" then system.asInstanceOf[TestSystem].stepUntilComplete()

    completer.waitForCompletion()

    system.shutdown()
