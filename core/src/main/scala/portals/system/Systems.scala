package portals

trait Systems

object Systems extends Systems:
  def default(): PortalsSystem = test()

  def test(): TestSystem = new TestSystem()

  def test(seed: Int): TestSystem = new TestSystem(Some(seed))

  // TODO: create test utilities to test both runtimes at the same time.
  // def test(): TestSystem =
  //   val _x = new ParallelSystem
  //   new TestSystem {
  //     override def launch(application: Application): Unit = _x.launch(application)
  //     override def shutdown(): Unit = _x.shutdown()
  //     override def step(): Unit = Thread.sleep(500)
  //     override def stepUntilComplete(): Unit = Thread.sleep(500)
  //   }

  def parallel(): PortalsSystem = new ParallelSystem()
