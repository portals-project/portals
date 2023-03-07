package portals

trait Systems

import portals.system.InterpreterSystem

object Systems extends Systems:
  def default(): PortalsSystem = interpreter()

  def interpreter(): InterpreterSystem = new InterpreterSystem()

  def interpreter(seed: Int): InterpreterSystem = new InterpreterSystem(Some(seed))

  // TODO: create test utilities to test both runtimes at the same time.
  // def test(): TestSystem =
  //   val _x = new ParallelSystem
  //   new TestSystem {
  //     override def launch(application: Application): Unit = _x.launch(application)
  //     override def shutdown(): Unit = _x.shutdown()
  //     override def step(): Unit = Thread.sleep(500)
  //     override def stepUntilComplete(): Unit = Thread.sleep(500)
  //   }

  def local(): PortalsSystem = new LocalSystem()
