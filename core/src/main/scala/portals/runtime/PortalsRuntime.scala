package portals

trait PortalsRuntime:
  def launch(application: Application): Unit
  def shutdown(): Unit
