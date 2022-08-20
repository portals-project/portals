package portals.benchmarks

class BenchmarkRunner():
  import BenchmarkRunner.*

  private val config = new BenchmarkConfig
  config.set("--iterations", 5)

  def warmup(benchmark: Benchmark, args: List[String] = List.empty) =
    config.parseArgs(args)
    benchmark.initialize(args)
    for (i <- 1 to config.getInt("--iterations")) {
      benchmark.runOneIteration()
    }
    benchmark.runOneIteration()
    benchmark.cleanupOneIteration()

  def run(benchmark: Benchmark, args: List[String] = List.empty) =
    config.parseArgs(args)
    benchmark.initialize(args)

    val timer = BenchmarkTimer()

    for (i <- 1 to config.getInt("--iterations")) {
      timer.run { benchmark.runOneIteration() }
    }

    benchmark.cleanupOneIteration()

    println(config.args)
    println(timer.statistics)

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

    def statistics: String = "Average: " + toSeconds(average(results)) + " seconds"
