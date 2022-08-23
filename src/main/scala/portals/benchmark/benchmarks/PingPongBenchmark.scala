package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object PingPongBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
  config.set("--nEvents", 1024 * 512) // number of events

  private case class Ping(i: Int)

  // creates a pingerponger application instance
  private def pinger(appName: String, completer: CompletionWatcher): Application =
    val builder = ApplicationBuilders.application(appName)

    val sequencer = builder.sequencers("sequencer").random[Ping]()

    val workflow = builder
      .workflows[Ping, Ping]("workflow")
      .source(sequencer.stream)
      .map { case Ping(i) =>
        if i == 0 then completer.complete()
        Ping(i - 1)
      }
      .sink()
      .freeze()

    builder.build()

  override val name = "PingPongBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = () // do nothing

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")

    val completer = CompletionWatcher()

    val system = Systems.asyncLocal()

    // create pinger and ponger apps
    val pingerApp = pinger("pinger", completer)
    val pongerApp = pinger("ponger", completer)

    // connect pinger to ponger and data generator
    val builder = ApplicationBuilders.application("runOneIteration")
    val pingerStream = builder.registry.streams.get[Ping]("/pinger/workflows/workflow/stream")
    val pongerStream = builder.registry.streams.get[Ping]("/ponger/workflows/workflow/stream")
    val pingerSequencer = builder.registry.sequencers.get[Ping]("/pinger/sequencers/sequencer")
    val pongerSequencer = builder.registry.sequencers.get[Ping]("/ponger/sequencers/sequencer")
    val _ = builder.connections.connect(pingerStream, pongerSequencer)
    val _ = builder.connections.connect(pongerStream, pingerSequencer)
    val generator = builder.generators.fromList(List(Ping(nEvents)))
    val _ = builder.connections.connect(generator.stream, pingerSequencer)
    val runApp = builder.build()

    // launch apps
    system.launch(pingerApp)
    system.launch(pongerApp)
    system.launch(runApp)

    // wait for completion
    completer.waitForCompletion()

    system.shutdown()
