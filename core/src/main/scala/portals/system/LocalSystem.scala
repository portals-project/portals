package portals

import portals.runtime.local.LocalRuntime

class LocalSystem extends PortalsSystem:
  private val runtime = LocalRuntime()

  /** Launch a Portals application. */
  def launch(application: Application): Unit = runtime.launch(application)

  /** Terminate the system and cleanup. */
  def shutdown(): Unit = runtime.shutdown()
