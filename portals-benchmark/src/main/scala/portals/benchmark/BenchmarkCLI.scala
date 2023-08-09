package portals.benchmark

import portals.benchmark.benchmarks.*

@main def BenchmarkCLI(): Unit =
  //////////////////////////////////////////////////////////////////////////////
  // Pekko Benchmarks
  //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "PekkoBenchmarks")
  //     .setParam("--sSystem", "Pekko")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 1024 * 2)
  //     .setParam("--nActors", 128)
  //     .set("--sWorkload", "pingPong", "threadRing", "countingActor")
  //   grid.configs.foreach { config =>
  //     val benchmark = PekkoBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "PekkoBenchmarks")
  //     .setParam("--sSystem", "async")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 1024)
  //     .setParam("--nAtomSize", 128)
  //     .setParam("--nChainLength", 128)
  //     .setParam("--nWorkflows", 128)
  //     .set("--sWorkload", "pingPong")
  //   grid.configs.foreach { config =>
  //     val benchmark = config.get("--sWorkload") match
  //       case "pingPong" => PingPongBenchmark
  //       case "threadRing" => ThreadRingTasks
  //       case "countingActor" => CountingActorBenchmark
  //       case "forkJoinThroughput" => ForkJoinThroughputBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "PekkoBenchmarks")
  //     .setParam("--sSystem", "async")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 1024 * 2)
  //     .setParam("--nAtomSize", 128)
  //     .setParam("--nChainLength", 128)
  //     .setParam("--nWorkflows", 128)
  //     .set("--sWorkload", "threadRing", "countingActor")
  //   grid.configs.foreach { config =>
  //     val benchmark = config.get("--sWorkload") match
  //       case "pingPong" => PingPongBenchmark
  //       case "threadRing" => ThreadRingTasks
  //       case "countingActor" => CountingActorBenchmark
  //       case "forkJoinThroughput" => ForkJoinThroughputBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // ------ SAVINA Micro-benchmarks ------
  // //////////////////////////////////////////////////////////////////////////////

  // //////////////////////////////////////////////////////////////////////////////
  // // PingPong Benchmark
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 256)
  //     .set("--sSystem", "test", "parallel")
  //   grid.configs.foreach { config =>
  //     val benchmark = PingPongBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  //////////////////////////////////////////////////////////////////////////////
  // ThreadRing Benchmark: Tasks
  //////////////////////////////////////////////////////////////////////////////
  {
    val grid = BenchmarkGrid()
      .setParam("--nIterations", 5)
      .setParam("--nEvents", 18 * 18)
      .set("--sSystem", "test", "parallel")
      .set("--nChainLength", 1, 2, 4, 8, 16, 32, 64, 128, 256, 1024)
    grid.configs.foreach { config =>
      val benchmark = ThreadRingTasks
      val runner = BenchmarkRunner()
      runner.warmup(benchmark, config.args)
      runner.run(benchmark, config.args)
    }
  }

  // //////////////////////////////////////////////////////////////////////////////
  // // ThreadRing Benchmark: Workflows
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 128)
  //     .set("--sSystem", "async", "noGuarantees", "microBatching", "sync")
  //     .set("--nChainLength", 1, 2, 4, 8, 16, 32, 64, 128, 256, 1024, 2048, 4096)
  //   grid.configs.foreach { config =>
  //     val benchmark = ThreadRingWorkflows
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // ThreadRing Benchmark: Workflows Alternating Sequencers
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 512)
  //     .set("--sSystem", "async", "noGuarantees", "microBatching")
  //     .set("--nChainLength", 1, 2, 4, 8, 16, 32, 64, 128, 256, 1024)
  //   grid.configs.foreach { config =>
  //     val benchmark = ThreadRingWorkflowsAlternatingSequencers
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // Counting Actor Benchmark
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 1024)
  //     .set("--sSystem", "async", "noGuarantees", "microBatching")
  //     .set("--nAtomSize", 1, 2, 4, 8, 16, 32, 64, 128, 256, 1024, 2048, 4096, 8192)
  //   grid.configs.foreach { config =>
  //     val benchmark = CountingActorBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // Fork Join Throughput Benchmark
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 128) // events in total over all WFs, not per WF
  //     .setParam("--nAtomSize", 128)
  //     .set("--sSystem", "async", "noGuarantees", "microBatching")
  //     .set("--nWorkflows", 1, 2, 4, 8, 16, 32, 64)
  //   grid.configs.foreach { config =>
  //     val benchmark = ForkJoinThroughputBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // /*
  // //////////////////////////////////////////////////////////////////////////////
  // // Fork Join Actor Creation Benchmark (TBD with virtual actors)
  // //////////////////////////////////////////////////////////////////////////////

  // //////////////////////////////////////////////////////////////////////////////
  // // Fibonacci (TBD with virtual actors)
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val benchmark = FibonacciBenchmark
  //   val config = BenchmarkConfig()
  //     .set("--iterations", 5)
  //     .set("--nFib", 90) // memoized

  //   val runner = BenchmarkRunner()
  //   runner.warmup(benchmark, config.args)
  //   runner.run(benchmark, config.args)
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // Chameneos (TBD with virtual actors)
  // //////////////////////////////////////////////////////////////////////////////

  // //////////////////////////////////////////////////////////////////////////////
  // // Big (TBD with virtual actors)
  // //////////////////////////////////////////////////////////////////////////////
  //  */

  // //////////////////////////////////////////////////////////////////////////////
  // // ------ DataParallelThroughputBenchmark ------
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "partitions")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 1024 * 2) // events in total over all WFs, not per WF
  //     .setParam("--nAtomSize", 128)
  //     .setParam("--nParallelism", 16)
  //     .setParam("--nChainLength", 16)
  //     .set("--sWorkload", "countingActor", "pingPong", "threadRingTasks")
  //     .set("--nPartitions", 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)
  //   grid.configs.foreach { config =>
  //     val benchmark = DataParallelThroughputBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "parallelism")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 1024 * 2) // events in total over all WFs, not per WF
  //     .setParam("--nAtomSize", 128)
  //     .setParam("--nPartitions", 16)
  //     .setParam("--nChainLength", 16)
  //     .set("--sWorkload", "countingActor", "pingPong", "threadRingTasks")
  //     .set("--nParallelism", 1, 2, 4, 8, 16, 32)
  //   grid.configs.foreach { config =>
  //     val benchmark = DataParallelThroughputBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // ------ Alignment Benchmark ------
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("--nIterations", 5)
  //     .set("--sSystem", "async", "microBatching", "noGuarantees")
  //   grid.configs.foreach { config =>
  //     for (
  //       conf <-
  //         List(
  //           (1, 64),
  //           (2, 64),
  //           (4, 64),
  //           (8, 128),
  //           (16, 512),
  //           (32, 1024),
  //           (64, 1024),
  //           (128, 1024),
  //           (256, 1024),
  //           (512, 1024),
  //           (1024, 1024)
  //         )
  //     ) {
  //       config.set("--nAtomSize", conf._1)
  //       config.set("--nEvents", 512 * conf._2)
  //       val benchmark = AtomAlignmentBenchmark
  //       val runner = BenchmarkRunner()
  //       runner.warmup(benchmark, config.args)
  //       runner.run(benchmark, config.args)
  //     }
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "withWork")
  //     .setParam("--nIterations", 5)
  //     .setParam("--withWork", true)
  //     .set("--sSystem", "async", "microBatching", "noGuarantees")
  //   grid.configs.foreach { config =>
  //     for (
  //       conf <-
  //         List(
  //           (1, 128),
  //           (2, 128),
  //           (4, 128),
  //           (8, 128),
  //           (16, 128),
  //           (32, 128),
  //           (64, 128),
  //           (128, 128),
  //           (256, 128),
  //           (512, 128),
  //           (1024, 128)
  //         )
  //     ) {
  //       config.set("--nAtomSize", conf._1)
  //       config.set("--nEvents", 64 * conf._2)
  //       val benchmark = AtomAlignmentBenchmark
  //       val runner = BenchmarkRunner()
  //       runner.warmup(benchmark, config.args)
  //       runner.run(benchmark, config.args)
  //     }
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // ------ NEXMark Benchmark ------
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 512) // events in total over all WFs, not per WF
  //     .setParam("--nAtomSize", 1024)
  //     .set("--sSystem", "async", "microBatching", "sync")
  //     .set("--sQuery", "Query1", "Query2", "Query3", "Query4")
  //   grid.configs.foreach { config =>
  //     val benchmark = NEXMarkBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // //////////////////////////////////////////////////////////////////////////////
  // // Chain of Tasks with Work
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "--nChainLength")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024)
  //     .setParam("--nAtomSize", 1)
  //     .set("--sSystem", "async", "noGuarantees", "microBatching")
  //     .set("--nChainLength", 1, 2, 4, 8, 16, 32, 64, 128)
  //   // .set("--nChainLength", 128)
  //   grid.configs.foreach { config =>
  //     val benchmark = ChainOfTasksWithWork
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "--nAtomSize")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024)
  //     .setParam("--nChainLength", 128)
  //     .set("--sSystem", "async", "noGuarantees", "microBatching")
  //     .set("--nAtomSize", 1, 2, 4, 8, 16, 32, 64, 128, 256, 512)
  //   grid.configs.foreach { config =>
  //     val benchmark = ChainOfTasksWithWork
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }

  // //////////////////////////////////////////////////////////////////////////////
  // // ------ SYNC Benchmark ------
  // //////////////////////////////////////////////////////////////////////////////
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "syncbenchmark")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 256)
  //     .set("--sSystem", "async", "sync")
  //   grid.configs.foreach { config =>
  //     val benchmark = PingPongBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "syncbenchmark")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 1024)
  //     .set("--sSystem", "async", "sync")
  //     .set("--nChainLength", 128)
  //   grid.configs.foreach { config =>
  //     val benchmark = ThreadRingTasks
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "syncbenchmark")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 1024 * 2)
  //     .set("--sSystem", "async", "sync")
  //     .set("--nAtomSize", 128)
  //   grid.configs.foreach { config =>
  //     val benchmark = CountingActorBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "syncbenchmark")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024)
  //     .setParam("--nAtomSize", 1)
  //     .set("--sSystem", "async", "sync")
  //     .set("--nChainLength", 128)
  //   grid.configs.foreach { config =>
  //     val benchmark = ChainOfTasksWithWork
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
  // {
  //   val grid = BenchmarkGrid()
  //     .setParam("tag", "syncbenchmark")
  //     .setParam("--nIterations", 5)
  //     .setParam("--nEvents", 1024 * 512) // events in total over all WFs, not per WF
  //     .setParam("--nAtomSize", 1024)
  //     .set("--sSystem", "async", "sync")
  //     .set("--sQuery", "Query1", "Query2", "Query3", "Query4")
  //   grid.configs.foreach { config =>
  //     val benchmark = NEXMarkBenchmark
  //     val runner = BenchmarkRunner()
  //     runner.warmup(benchmark, config.args)
  //     runner.run(benchmark, config.args)
  //   }
  // }
