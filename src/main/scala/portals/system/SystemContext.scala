package portals

trait SystemContext:
  val registry: GlobalRegistry

  def launch(workflow: Workflow): Unit

  def shutdown(): Unit

trait LocalSystemContext extends SystemContext:
  def step(): Unit
  def step(wf: Workflow): Unit
  def stepAll(): Unit
  def stepAll(wf: Workflow): Unit
  def isEmpty(): Boolean
  def isEmpty(wf: Workflow): Boolean