package portals.benchmark.benchmarks

import portals.*
import portals.api.builder.ApplicationBuilder
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object ThreadRingWorkflowsAlternatingSequencers extends Benchmark:
  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nChainLength") // 128
    .setRequired("--sSystem") // async

  override val name = "ThreadRingWorkflowsAlternatingSequencers"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val chainLength = config.getInt("--nChainLength")
    val sSystem = config.get("--sSystem")

    val completer = CompletionWatcher()

    val system = sSystem match
      case "async" => Systems.local()
      case "noGuarantees" => Systems.asyncLocalNoGuarantees()
      case "microBatching" => Systems.asyncLocalMicroBatching()
      case "sync" => Systems.test()
      case _ => ???

    val builder = ApplicationBuilder("app")

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
    val completes = builder
      .workflows[Int, Int]("completer")
      .source(prev.stream)
      .task(completer.task(_ == 0))
      .processor(x => if x != 0 then ctx.emit(x))
      .sink()
      .freeze()

    // cycle back
    val _ = builder.connections.connect(completes.stream, sequencer)

    val application = builder.build()

    system.launch(application)

    if sSystem == "sync" then system.asInstanceOf[TestSystem].stepUntilComplete()

    completer.waitForCompletion()

    system.shutdown()
