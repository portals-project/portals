package portals.benchmark

class BenchmarkRunner():
  import BenchmarkRunner.*

  private val config = new BenchmarkConfig
  config.setRequired("--nIterations") // 5

  def warmup(benchmark: Benchmark, args: List[String] = List.empty) =
    config.parseArgs(args)
    benchmark.initialize(args)
    for (i <- 1 to config.getInt("--nIterations")) {
      benchmark.runOneIteration()
      benchmark.cleanupOneIteration()
    }

  // TODO: this should be guarded against long running blocking executions
  def run(benchmark: Benchmark, args: List[String] = List.empty) =
    config.parseArgs(args)
    benchmark.initialize(args)

    val timer = BenchmarkTimer()

    for (i <- 1 to config.getInt("--nIterations")) {
      timer.run { benchmark.runOneIteration() }
      benchmark.cleanupOneIteration()
    }

    println("name " + benchmark.name + " " + args.mkString(" ") + " " + timer.statistics)

object BenchmarkRunner:
  class BenchmarkTimer():
    var results = List[Long]()

    def run(f: => Unit) = {
      val tStart = System.nanoTime()
      f
      val tStop = System.nanoTime()
      results ::= (tStop - tStart)
    }

    private def toSeconds(nanos: Long) = nanos / 1000000000.0
    private def toMillis(nanos: Long) = nanos / 1000000.0
    private def toMicros(nanos: Long) = nanos / 1000.0
    private def toNanos(nanos: Long) = nanos

    private def average(results: List[Long]) = results.sum / results.length

    def statistics: String = "average(s) " + toSeconds(average(results))
