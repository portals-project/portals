package portals

import portals.*

/** Test system and runtime for Portals. This system is single-threaded,
  * synchronous, and lets the user proceed the computation by taking steps over
  * atoms. Alternatively, the computation can be carried out until the end by
  * stepping until it has completed.
  */
class TestSystem(seed: Option[Int] = None) extends PortalsSystem:
  private val runtime = TestRuntime(seed)

  /** Launch a Portals application. */
  def launch(application: Application): Unit = runtime.launch(application)

  /** Take a step over an atom. */
  def step(): Unit = runtime.step()

  /** Take steps until completion. */
  def stepUntilComplete(): Unit = runtime.stepUntilComplete()

  /** Terminate the system and cleanup. */
  def shutdown(): Unit = runtime.shutdown()
