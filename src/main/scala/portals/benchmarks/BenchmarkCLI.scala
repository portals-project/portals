package portals.benchmarks

@main def BenchmarkCLI(): Unit =
  // PingPong Benchmark
  {
    val benchmark = Benchmarks.PingPongBenchmark
    val config = BenchmarkConfig()
      .set("--iterations", 10)
      .set("--nEvents", 1024 * 512)

    val runner = BenchmarkRunner()
    runner.run(benchmark, config.args)
  }
  // Alignment Benchmark
  {
    for (stepSize <- List(1, 2, 4, 8, 16, 32)) {
      val benchmark = Benchmarks.AtomAlignmentBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 10)
        .set("--nEvents", 1024 * stepSize)
        .set("--stepSize", stepSize)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }

    for (stepSize <- List(32, 64, 128, 256, 512, 1024)) {
      val benchmark = Benchmarks.AtomAlignmentBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 3)
        .set("--nEvents", 1024 * stepSize)
        .set("--stepSize", stepSize)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }
  // Chain of Workflows Benchmark
  {
    for (chainLength <- List(1, 2, 4, 8, 16, 32, 64, 128)) {
      val benchmark = Benchmarks.ChainOfWorkflowsBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 5)
        .set("--nEvents", 1024 * 128)
        .set("--chainLength", chainLength)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }
  // Chain of Tasks Benchmark
  {
    for (chainLength <- List(1, 2, 4, 8, 16, 32, 64, 128)) {
      val benchmark = Benchmarks.ChainOfTasksBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 5)
        .set("--nEvents", 1024 * 1024)
        .set("--chainLength", chainLength)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }
