package portals

import portals.application.Application

trait PortalsSystem:
  /** Launch a Portals application. */
  def launch(application: Application): Unit

  /** Terminate the system and cleanup. */
  def shutdown(): Unit
