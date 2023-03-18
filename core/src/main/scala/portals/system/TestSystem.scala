package portals.system

import portals.application.Application
import portals.runtime.test.TestRuntime
import portals.system.PortalsSystem

/** Test system and runtime for Portals. This system is single-threaded,
  * synchronous, and lets the user proceed the computation by taking steps over
  * atoms. Alternatively, the computation can be carried out until the end by
  * stepping until it has completed.
  */
class TestSystem(seed: Option[Int] = None) extends PortalsSystem:
  private val runtime: TestRuntime = TestRuntime(seed)

  /** Launch a Portals application. */
  def launch(application: Application): Unit = runtime.launch(application)

  /** Take a step over an atom. */
  def step(): Unit = runtime.step()

  /** Check if the system can take a step. */
  def canStep(): Boolean = runtime.canStep()

  /** Take steps until completion. */
  def stepUntilComplete(): Unit = runtime.stepUntilComplete()

  /** Take steps until completion or until reaching the max number of steps. */
  def stepUntilComplete(max: Int): Unit = runtime.stepUntilComplete(max)

  /** Terminate the system and cleanup. */
  def shutdown(): Unit = runtime.shutdown()

  def registry = runtime.registry
