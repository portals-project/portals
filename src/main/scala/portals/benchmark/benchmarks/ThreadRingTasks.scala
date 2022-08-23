package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object ThreadRingTasks extends Benchmark:
  private val config = BenchmarkConfig()
  config.set("--nEvents", 1024 * 1024) // number of events
  config.set("--nChainLength", 128) // chain length

  override val name = "ThreadRingTasks"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val chainLength = config.getInt("--nChainLength")

    val completer = CompletionWatcher()

    val system = Systems.asyncLocal()

    val builder = ApplicationBuilders.application("app")

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
      .sink()
      .freeze()

    // loop back
    val _ = builder.connections.connect(wf.stream, sequencer)

    val application = builder.build()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()
