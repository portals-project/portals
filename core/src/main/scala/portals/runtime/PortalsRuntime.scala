package portals.runtime

import portals.application.Application

trait PortalsRuntime:
  def launch(application: Application): Unit
  def shutdown(): Unit
