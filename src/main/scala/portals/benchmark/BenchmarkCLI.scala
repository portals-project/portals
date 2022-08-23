package portals.benchmark

import portals.benchmark.benchmarks.*

@main def BenchmarkCLI(): Unit =
  /*
  //////////////////////////////////////////////////////////////////////////////
  // Alignment Benchmark
  //////////////////////////////////////////////////////////////////////////////
  {
    for (stepSize <- List(1, 2, 4, 8, 16, 32)) {
      val benchmark = AtomAlignmentBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 10)
        .set("--nEvents", 1024 * stepSize)
        .set("--stepSize", stepSize)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }

    for (stepSize <- List(32, 64, 128, 256, 512, 1024)) {
      val benchmark = AtomAlignmentBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 3)
        .set("--nEvents", 1024 * stepSize)
        .set("--stepSize", stepSize)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }
  //////////////////////////////////////////////////////////////////////////////
  // Alignment Benchmark Baseline
  //////////////////////////////////////////////////////////////////////////////
  {
    for (stepSize <- List(1, 2, 4, 8, 16, 32)) {
      val benchmark = AtomAlignmentWithoutAlignmentBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 10)
        .set("--nEvents", 1024 * stepSize)
        .set("--stepSize", stepSize)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }

    for (stepSize <- List(32, 64, 128, 256, 512, 1024)) {
      val benchmark = AtomAlignmentWithoutAlignmentBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 3)
        .set("--nEvents", 1024 * stepSize)
        .set("--stepSize", stepSize)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }
  //////////////////////////////////////////////////////////////////////////////
  // Chain of Workflows Benchmark
  //////////////////////////////////////////////////////////////////////////////
  {
    for (chainLength <- List(1, 2, 4, 8, 16, 32, 64, 128)) {
      val benchmark = ChainOfWorkflowsBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 5)
        .set("--nEvents", 1024 * 1024)
        .set("--chainLength", chainLength)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }
  //////////////////////////////////////////////////////////////////////////////
  // Chain of Tasks Benchmark
  //////////////////////////////////////////////////////////////////////////////
  {
    for (chainLength <- List(1, 2, 4, 8, 16, 32, 64, 128)) {
      val benchmark = ChainOfTasksBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 5)
        .set("--nEvents", 1024 * 1024)
        .set("--chainLength", chainLength)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }


  //////////////////////////////////////////////////////////////////////////////
  // SAVINA Micro-benchmarks
  //////////////////////////////////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////
  // PingPong Benchmark
  //////////////////////////////////////////////////////////////////////////////
  {
    val benchmark = PingPongBenchmark
    val config = BenchmarkConfig()
      .set("--iterations", 10)
      .set("--nEvents", 1024*512) // N

    val runner = BenchmarkRunner()
    runner.warmup(benchmark, config.args)
    runner.run(benchmark, config.args)
  }

  // //////////////////////////////////////////////////////////////////////////////
  // // ThreadRing Benchmark: Tasks
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   for (chainLength <- List(1, 2, 4, 8, 16, 32, 64, 128, 256, 1024)) {
  //     val benchmark = ThreadRingTasks
  //     val config = BenchmarkConfig()
  //       .set("--iterations", 10)
  //       .set("--nChainLength", chainLength)
  //       .set("--nEvents", 1024*1024)

  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // ThreadRing Benchmark: Workflows
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   for (chainLength <- List(1, 2, 4, 8, 16, 32, 64, 128, 256, 1024, 2048, 4096, 8192)) {
  //     val benchmark = ThreadRingWorkflows
  //     val config = BenchmarkConfig()
  //       .set("--iterations", 5)
  //       .set("--nChainLength", chainLength)
  //       .set("--nEvents", 1024*128)

  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // ThreadRing Benchmark: Workflows Alternating Sequencers
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   for (chainLength <- List(1, 2, 4, 8, 16, 32, 64, 128, 256, 1024, 2048, 4096, 8192)) {
  //     val benchmark = ThreadRingWorkflowsAlternatingSequencers
  //     val config = BenchmarkConfig()
  //       .set("--iterations", 5)
  //       .set("--nChainLength", chainLength)
  //       .set("--nEvents", 1024*128)

  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // ThreadRing Benchmark: Tasks
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   for (chainLength <- List(1, 2, 4, 8, 16, 32, 64, 128, 256, 1024, 2048, 4096, 8192)) {
  //     val benchmark = ThreadRingTasks
  //     val config = BenchmarkConfig()
  //       .set("--iterations", 5)
  //       .set("--nChainLength", chainLength)
  //       .set("--nEvents", 1024*128)

  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  //////////////////////////////////////////////////////////////////////////////
  // Counting Actor Benchmark
  //////////////////////////////////////////////////////////////////////////////
  {
    for (atomSize <- List(1, 2, 4, 8, 16, 32, 64, 128, 256, 1024, 2048, 4096, 8192)) {
      val benchmark = CountingActorBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 5)
        .set("--nEvents", 1024*1024)
        .set("--nAtomSize", atomSize)

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }


  // //////////////////////////////////////////////////////////////////////////////
  // // Fork Join Throughput Benchmark
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val benchmark = ForkJoinThroughputBenchmark
  //   val config = BenchmarkConfig()
  //     .set("--iterations", 5)
  //     .set("--nEvents", 1024 * 1024)
  //     .set("--nWorkflows", 16)
  //     .set("--nAtomSize", 1024)

  //   val runner = BenchmarkRunner()
  //   runner.warmup(benchmark, config.args)
  //   runner.run(benchmark, config.args)
  // }

  //////////////////////////////////////////////////////////////////////////////
  // Fork Join Actor Creation Benchmark (TBD with virtual actors)
  //////////////////////////////////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////
  // Fibonacci (TBD with virtual actors)
  //////////////////////////////////////////////////////////////////////////////
  {
    val benchmark = FibonacciBenchmark
    val config = BenchmarkConfig()
      .set("--iterations", 5)
      .set("--nFib", 90) // memoized

    val runner = BenchmarkRunner()
    runner.warmup(benchmark, config.args)
    runner.run(benchmark, config.args)
  }

  //////////////////////////////////////////////////////////////////////////////
  // Chameneos (TBD with virtual actors)
  //////////////////////////////////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////
  // Big (TBD with virtual actors)
  //////////////////////////////////////////////////////////////////////////////

   */

  //////////////////////////////////////////////////////////////////////////////
  // DataParallelThroughputBenchmark
  //////////////////////////////////////////////////////////////////////////////
  {
    for (nPartitions <- List(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)) {
      val benchmark = DataParallelThroughputBenchmark
      val config = BenchmarkConfig()
        .set("--iterations", 5) // number of iterations
        .set("--nEvents", 1024 * 1024) // number of events
        .set("--nPartitions", nPartitions) // k, number of workflows
        .set("--nAtomSize", 128) // atomSize

      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }
