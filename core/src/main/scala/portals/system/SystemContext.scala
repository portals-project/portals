package portals

trait SystemContext:
  def launch(application: Application): Unit
  def shutdown(): Unit

trait LocalSystemContext extends SystemContext:
  def step(): Unit
  def stepAll(): Unit
  def isEmpty(): Boolean
