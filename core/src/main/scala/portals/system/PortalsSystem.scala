package portals

trait PortalsSystem:
  def launch(application: Application): Unit
  def shutdown(): Unit
