package portals.benchmark.benchmarks

import portals.api.builder.ApplicationBuilder
import portals.api.dsl.DSL.*
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.system.Systems
import portals.system.TestSystem

object ChainOfTasksWithWork extends Benchmark:
  private val config = BenchmarkConfig()
  config.setRequired("--nEvents") // number of events
  config.setRequired("--nAtomSize") // atom size
  config
    .setRequired("--nChainLength") // chain length
    .setRequired("--sSystem") // "async"

  override val name = "ChainOfTasksWithWork"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nAtomSize = config.getInt("--nAtomSize")
    val nChainLength = config.getInt("--nChainLength")
    val sSystem = config.get("--sSystem")

    val completer = CompletionWatcher()

    val system = sSystem match
      case "parallel" => Systems.parallel(8)
      case "test" => Systems.test()
      case _ => ???

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, nEvents, nAtomSize)

    var prev = builder
      .workflows[Int, Int]("workflow")
      .source[Int](generator.stream)

    Range(0, nChainLength).foreach { i =>
      prev = prev.map { x =>
        Computation(1024)
        x
      }
    }

    prev
      .task { completer.task { _ == nEvents - 1 } }
      .sink()
      .freeze()

    val application = builder.build()

    system.launch(application)

    if sSystem == "sync" then system.asInstanceOf[TestSystem].stepUntilComplete()

    completer.waitForCompletion()

    system.shutdown()
