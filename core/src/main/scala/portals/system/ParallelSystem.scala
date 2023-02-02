package portals

class ParallelSystem extends PortalsSystem:
  private val runtime = ParallelRuntime()

  /** Launch a Portals application. */
  def launch(application: Application): Unit = runtime.launch(application)

  /** Terminate the system and cleanup. */
  def shutdown(): Unit = runtime.shutdown()
