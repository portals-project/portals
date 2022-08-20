package portals.benchmarks

import scala.concurrent.duration.*
import scala.concurrent.Await
import scala.concurrent.Promise

import portals.*
import portals.DSL.*

object Benchmarks:
  private class CompletionWatcher():
    private val atMost = 10.seconds
    private val promise = Promise[Boolean]()
    private val future = promise.future

    def complete(value: Boolean = true): Unit =
      if !promise.isCompleted then promise.success(value)

    def waitForCompletion(): Boolean =
      Await.result(future, atMost)

    def task[T](p: T => Boolean) = Tasks.map[T, T] { x => { if p(x) then complete(true); x } }

    def workflow[T](stream: AtomicStreamRef[T], builder: ApplicationBuilder)(p: T => Boolean): Workflow[T, T] =
      builder
        .workflows[T, T]("completer")
        .source[T](stream)
        .task(task(p(_)))
        .sink[T]()
        .freeze()

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

  object AtomAlignmentBenchmark extends Benchmark:

    private val config = BenchmarkConfig()
    config.set("--nEvents", 1024 * 1024) // number of events
    config.set("--stepSize", 1024) // atom size

    override def initialize(args: List[String]): Unit = config.parseArgs(args)

    override def cleanupOneIteration(): Unit = () // do nothing

    override def runOneIteration(): Unit =
      val nEvents = config.getInt("--nEvents")
      val stepSize = config.getInt("--stepSize")

      val completer = CompletionWatcher()

      val system = Systems.asyncLocal()

      val builder = ApplicationBuilders.application("runOneIteration")

      // generator
      val generator = builder.generators.fromRange(0, nEvents, stepSize)

      // create grid
      val forwarder = Tasks.flatMap[Int, Int](x => List(x))
      val silent = Tasks.flatMap[Int, Int](x => List.empty[Int])

      val wfb = builder.workflows[Int, Int]("workflow")

      val source = wfb.source(generator.stream)
      val p11 = source.task(forwarder)
      val p12 = source.task(silent)
      val p13 = source.task(silent)
      // TODO: change here so we can use wfb instead of `source`, or other options
      val p21 = source.from(p11, p12, p13)(forwarder)
      val p22 = source.from(p11, p12, p13)(silent)
      val p23 = source.from(p11, p12, p13)(silent)
      val compl = source.from(p21, p22, p23)(completer.task { _ == nEvents - 1 })
      val sink = compl.sink()
      val workflow = sink.freeze()
      val app = builder.build()

      system.launch(app)

      // TODO: should trigger shutdown even if Await is timed out
      completer.waitForCompletion()

      system.shutdown()

  object ChainOfWorkflowsBenchmark extends Benchmark:
    private val config = BenchmarkConfig()
    config.set("--nEvents", 1024 * 1024) // number of events
    config.set("--stepSize", 128) // atom size
    config.set("--chainLength", 128) // chain length

    override def initialize(args: List[String]): Unit =
      config.parseArgs(args)

    override def cleanupOneIteration(): Unit = ()

    override def runOneIteration(): Unit =
      val nEvents = config.getInt("--nEvents")
      val stepSize = config.getInt("--stepSize")
      val chainLength = config.getInt("--chainLength")

      val completer = CompletionWatcher()

      val system = Systems.asyncLocal()

      val builder = ApplicationBuilders.application("app")

      val generator = builder.generators.fromRange(0, nEvents, stepSize)

      def workflowFactory(name: String, stream: AtomicStreamRef[Int]): Workflow[Int, Int] =
        builder
          .workflows[Int, Int](name)
          .source[Int](stream)
          .map(_ + 1)
          .sink()
          .freeze()

      var prev: Workflow[Int, Int] = workflowFactory("wf0", generator.stream)
      Range(1, chainLength).foreach { i =>
        prev = workflowFactory("wf" + i, prev.stream)
      }

      // completer
      completer.workflow(prev.stream, builder) { _ == nEvents - 1 }

      val application = builder.build()

      system.launch(application)

      completer.waitForCompletion()

      system.shutdown()

  object ChainOfTasksBenchmark extends Benchmark:
    private val config = BenchmarkConfig()
    config.set("--nEvents", 1024 * 1024) // number of events
    config.set("--stepSize", 128) // atom size
    config.set("--chainLength", 128) // chain length

    override def initialize(args: List[String]): Unit =
      config.parseArgs(args)

    override def cleanupOneIteration(): Unit = ()

    override def runOneIteration(): Unit =
      val nEvents = config.getInt("--nEvents")
      val stepSize = config.getInt("--stepSize")
      val chainLength = config.getInt("--chainLength")

      val completer = CompletionWatcher()

      val system = Systems.asyncLocal()

      val builder = ApplicationBuilders.application("app")

      val generator = builder.generators.fromRange(0, nEvents, stepSize)

      var prev = builder
        .workflows[Int, Int]("workflow")
        .source[Int](generator.stream)

      Range(0, chainLength).foreach { i =>
        prev = prev.map { x => x }
      }

      prev
        .task { completer.task { _ == nEvents - 1 } }
        .sink()
        .freeze()

      val application = builder.build()

      system.launch(application)

      completer.waitForCompletion()

      system.shutdown()
