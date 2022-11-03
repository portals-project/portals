package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object DataParallelThroughputBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nPartitions") // 128
    .setRequired("--nParallelism") // 16
    .setRequired("--nAtomSize") // 128
    .setRequired("--sWorkload") // countingActor

  private def countingActorWorkload(
      builder: ApplicationBuilder,
      completer: CountingCompletionWatcher,
      config: BenchmarkConfig
  ): Unit =
    val nEvents = config.getInt("--nEvents")
    val nAtomSize = config.getInt("--nAtomSize")
    val nPartitions = config.getInt("--nPartitions")

    val generator = builder.generators.generator[Int](DataParallel.fromRange(0, nEvents, nAtomSize))

    var workflow = builder
      .workflows[Int, Int]("workflow")
      .source[Int](generator.stream)
      .init {
        val state = PerTaskState("state", 0)
        Tasks.map { x =>
          state.set(state.get() + 1)
          if state.get() == (nEvents / nPartitions) then completer.complete()
          x
        }
      }
      .sink()
      .freeze()

  private def pingPongWorkload(
      builder: ApplicationBuilder,
      completer: CountingCompletionWatcher,
      config: BenchmarkConfig
  ): Unit =
    case class Ping(i: Int)

    val nEvents = config.getInt("--nEvents")
    val nPartitions = config.getInt("--nPartitions")

    // val generator = builder.generators.generator[Int](DataParallel.fromRange(0, nEvents, nAtomSize))
    val generator = builder.generators.generator(DataParallel.fromList(List(Ping(nEvents / nPartitions))))

    val pingersequencer = builder.sequencers("pingersequencer").random[Ping]()
    val pongersequencer = builder.sequencers("pongersequencer").random[Ping]()

    def pingerPonger(name: String, stream: AtomicStreamRefKind[Ping]): Workflow[Ping, Ping] =
      builder
        .workflows[Ping, Ping](name)
        .source(stream)
        .flatMap { case Ping(i) =>
          if i > 0 then List(Ping(i - 1))
          else
            completer.complete()
            List.empty
        }
        .sink()
        .freeze()

    val pinger = pingerPonger("pinger", pingersequencer.stream)
    val ponger = pingerPonger("ponger", pongersequencer.stream)

    builder.connections.connect(ponger.stream, pingersequencer)
    builder.connections.connect(pinger.stream, pongersequencer)
    builder.connections.connect(generator.stream, pingersequencer)

  private def threadRingTasksWorkload(
      builder: ApplicationBuilder,
      completer: CountingCompletionWatcher,
      config: BenchmarkConfig
  ): Unit =
    val nEvents = config.getInt("--nEvents")
    val nPartitions = config.getInt("--nPartitions")
    val nChainLength = config.getInt("--nChainLength")

    val generator = builder.generators.generator(DataParallel.fromList(List(nEvents / nPartitions)))

    val sequencer = builder.sequencers.random[Int]()
    val _ = builder.connections.connect(generator.stream, sequencer)

    var prev = builder
      .workflows[Int, Int]("workflow")
      .source[Int](sequencer.stream)

    Range(0, nChainLength).foreach { i =>
      prev = prev.map { x => if x == 0 then x else x - 1 }
    }

    val wf = prev
      .task { completer.task { _ == 0 } }
      .processor { x => if x > 0 then ctx.emit(x) } // consume end event so it terminates
      .sink()
      .freeze()

    // loop back
    val _ = builder.connections.connect(wf.stream, sequencer)

  override val name = "DataParallelThroughputBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nPartitions = config.getInt("--nPartitions")
    val nAtomSize = config.getInt("--nAtomSize")
    val nParallelism = config.getInt("--nParallelism")
    val sWorkload = config.get("--sWorkload")

    val completer = CountingCompletionWatcher(nPartitions)

    val builder = ApplicationBuilders.application("app")

    sWorkload match
      case "countingActor" => countingActorWorkload(builder, completer, config)
      case "pingPong" => pingPongWorkload(builder, completer, config)
      case "threadRingTasks" => threadRingTasksWorkload(builder, completer, config)

    val application = builder.build()

    /*
    val generator = builder.generators.generator[Int](DataParallel.fromRange(0, nEvents, nAtomSize))

    val workflow = builder
      .workflows[Int, Int]("workflow")
      .source(generator.stream)
      .map { x => { if x > nEvents - nPartitions - 1 then completer.complete(); x } }
      .sink()
      .freeze()

    val application = builder.build()
     */

    val system = Systems.dataParallel(nPartitions, nParallelism)

    system.launch(application)

    completer.waitForCompletion()

    system.shutdown()
