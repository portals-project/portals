package portals.system

import portals.application.Application
import portals.runtime.parallel.ParallelRuntime

class ParallelSystem(nThreads: Int) extends PortalsSystem:
  val runtime = ParallelRuntime(nThreads)

  /** Launch a Portals application. */
  def launch(application: Application): Unit =
    runtime.launch(application)

  /** Terminate the system and cleanup. */
  def shutdown(): Unit =
    runtime.shutdown()
