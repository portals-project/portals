package portals.benchmark.benchmarks

import portals.*
import portals.api.builder.ApplicationBuilder
import portals.api.dsl.DSL.*
import portals.application.Application
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.system.InterpreterSystem
import portals.system.Systems

object PingPongBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--sSystem") // "async"

  private case class Ping(i: Int)

  // creates a pingerponger application instance
  private def pinger(appName: String, completer: CompletionWatcher): Application =
    val builder = ApplicationBuilder(appName)

    val sequencer = builder.sequencers("sequencer").random[Ping]()

    val workflow = builder
      .workflows[Ping, Ping]("workflow")
      .source(sequencer.stream)
      .flatMap { case Ping(i) =>
        if i > 0 then List(Ping(i - 1))
        else
          completer.complete()
          List.empty
      }
      .sink()
      .freeze()

    builder.build()

  override val name = "PingPongBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val sSystem = config.get("--sSystem")

    val completer = CompletionWatcher()

    val system = sSystem match
      case "async" => Systems.local()
      case "noGuarantees" => Systems.asyncLocalNoGuarantees()
      case "microBatching" => Systems.asyncLocalMicroBatching()
      case "sync" => Systems.interpreter()
      case _ => ???

    // create pinger and ponger apps
    val pingerApp = pinger("pinger", completer)
    val pongerApp = pinger("ponger", completer)

    // connect pinger to ponger and data generator
    val builder = ApplicationBuilder("runOneIteration")
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

    if sSystem == "sync" then system.asInstanceOf[InterpreterSystem].stepUntilComplete()

    // wait for completion
    try completer.waitForCompletion()
    catch case e => { system.shutdown(); throw e }

    system.shutdown()
