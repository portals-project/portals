package portals

trait SystemContext:
  val registry: GlobalRegistry

  def launch(application: Application): Unit

  def shutdown(): Unit

trait LocalSystemContext extends SystemContext:
  def step(): Unit
  def step(wf: Workflow[_, _]): Unit
  def stepAll(): Unit
  def stepAll(wf: Workflow[_, _]): Unit
  def isEmpty(): Boolean
  def isEmpty(wf: Workflow[_, _]): Boolean
