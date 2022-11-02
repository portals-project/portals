package portals

trait System:
  def launch(application: Application): Unit
  def shutdown(): Unit
