package portals.system.test

import portals.Application

class TestSystem():
  val runtime = TestRuntime()

  /** Launch a Portals application. */
  def launch(application: Application): Unit = runtime.launch(application)

  /** Take a step over an atom. */
  def step(): Unit = runtime.step()

  /** Take steps until completion. */
  def stepUntilComplete(): Unit = runtime.stepUntilComplete()

  /** Terminate the system and cleanup. */
  def shutdown(): Unit = runtime.shutdown()
