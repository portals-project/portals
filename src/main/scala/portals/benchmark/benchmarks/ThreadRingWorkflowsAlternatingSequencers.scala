package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object ThreadRingWorkflowsAlternatingSequencers extends Benchmark:
  private val config = BenchmarkConfig()
  config.set("--nEvents", 1024 * 1024) // number of events
  config.set("--nChainLength", 128) // chain length

  override val name = "ThreadRingWorkflowsAlternatingSequencers"

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

    def workflowFactory(name: String, stream: AtomicStreamRef[Int]): Workflow[Int, Int] =
      builder
        .workflows[Int, Int](name)
        .source[Int](stream)
        .map { x => if x > 0 then x - 1 else 0 }
        .sink()
        .freeze()

    val sequencer = builder.sequencers.random[Int]()
    val _ = builder.connections.connect(generator.stream, sequencer)

    var prevSequencer = builder.sequencers.random[Int]()
    val _ = builder.connections.connect(sequencer.stream, prevSequencer)
    var prev: Workflow[Int, Int] = workflowFactory("wf0", prevSequencer.stream)
    Range(1, chainLength).foreach { i =>
      prevSequencer = builder.sequencers.random[Int]()
      builder.connections.connect(prev.stream, prevSequencer)
      prev = workflowFactory("wf" + i, prevSequencer.stream)
    }

    // completer
    val completes = completer.workflow(prev.stream, builder) { _ == 0 }

    // cycle back
    val _ = builder.connections.connect(completes.stream, sequencer)

    val application = builder.build()

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()
