package portals.benchmark.benchmarks

import portals.api.builder.*
import portals.api.dsl.DSL.*
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.system.Systems
import portals.system.TestSystem

object CountingActorBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nAtomSize") // 128

  override val name = "CountingActorBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nAtomSize = config.getInt("--nAtomSize")
    val sSystem = config.get("--sSystem")

    val completer = CompletionWatcher()

    val system = sSystem match
      case "parallel" => Systems.parallel(8)
      case "test" => Systems.test()
      case _ => ???

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, nEvents, nAtomSize)

    var workflow = builder
      .workflows[Int, Int]("workflow")
      .source[Int](generator.stream)
      .init {
        var state: Int = 0
        TaskBuilder.map { x =>
          state += 1
          if state == nEvents - 1 then completer.complete(true)
          x
        }
      }
      .sink()
      .freeze()

    val application = builder.build()

    system.launch(application)

    if sSystem == "sync" then system.asInstanceOf[TestSystem].stepUntilComplete()

    completer.waitForCompletion()

    system.shutdown()
