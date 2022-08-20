package portals.benchmarks

// see https://github.com/shamsimam/savina/blob/master/src/main/java/edu/rice/habanero/benchmarks/Benchmark.java
trait Benchmark:
  def initialize(args: List[String]): Unit
  def runOneIteration(): Unit
  def cleanupOneIteration(): Unit
