package portals

trait SystemContext:
  // TODO: consider removing, if not used.
  val registry: GlobalRegistry

  def launch(application: Application): Unit

  def shutdown(): Unit

trait LocalSystemContext extends SystemContext:
  def step(): Unit
  def stepAll(): Unit
  def isEmpty(): Boolean
