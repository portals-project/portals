package portals.benchmark.benchmarks

import portals.api.builder.ApplicationBuilder
import portals.api.dsl.DSL.*
import portals.application.*
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.system.Systems
import portals.system.TestSystem

object ForkJoinThroughputBenchmark extends Benchmark:
  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nWorkflows") // 1024 * 1024
    .setRequired("--nAtomSize") // 128
    .setRequired("--sSystem") // "async"

  override val name = "ForkJoinThroughputBenchmark"

  override def initialize(args: List[String]): Unit =
    config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nWorkflows = config.getInt("--nWorkflows")
    val nAtomSize = config.getInt("--nAtomSize")
    val sSystem = config.get("--sSystem")

    val system = sSystem match
      case "parallel" => Systems.parallel(8)
      case "test" => Systems.test()
      case _ => ???

    val builder = ApplicationBuilder("app")

    val generator = builder.generators.fromRange(0, nEvents / nWorkflows, nAtomSize)

    // this is strange that the second option works better than this option, look more closely at this
    val completer = CountingCompletionWatcher(nWorkflows)
    def workflowFactory(name: String): Workflow[Int, Int] =
      builder
        .workflows[Int, Int](name)
        .source[Int](generator.stream)
        .map { x => { if x == (nEvents / nWorkflows) - 1 then completer.complete(); x } }
        .sink()
        .freeze()
    val workflows = Range(0, nWorkflows).map { i =>
      workflowFactory("workflow" + i)
    }

    // val completer = CompletionWatcher()
    // def workflowFactory(name: String): Workflow[Int, Int] =
    //   builder
    //     .workflows[Int, Int](name)
    //     .source[Int](generator.stream)
    //     .flatMap { x => if x == (nEvents / nWorkflows) - 1 then List(-1) else List.empty }
    //     .sink()
    //     .freeze()

    // val workflows = Range(0, nWorkflows).map { i =>
    //   workflowFactory("workflow" + i)
    // }

    // val sequencer = builder.sequencers.random[Int]()

    // workflows.foreach { x => builder.connections.connect(x.stream, sequencer) }

    // val completes = builder
    //   .workflows[Int, Int]("completes")
    //   .source(sequencer.stream)
    //   .init {
    //     var state = 0
    //     completer.task { x =>
    //       state += 1
    //       if state == nWorkflows then true else false
    //     }
    //   }
    //   .sink()
    //   .freeze()

    val application = builder.build()

    system.launch(application)

    if sSystem == "sync" then system.asInstanceOf[TestSystem].stepUntilComplete()

    completer.waitForCompletion()

    system.shutdown()
