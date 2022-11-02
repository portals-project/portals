package portals.system.test

import portals.Application

trait TestSystem:
  /** Launch a Portals application. */
  def launch(application: Application): Unit

  def step(): Unit

  def stepUntilComplete(): Unit

  /** Shutdown the system. */
  def shutdown(): Unit

class TestSystemImpl() extends TestSystem:
  val runtime = TestRuntimeImpl()

  override def launch(application: Application): Unit = runtime.launch(application)

  override def step(): Unit = runtime.step()

  override def stepUntilComplete(): Unit = runtime.stepUntilComplete()

  override def shutdown(): Unit = runtime.shutdown()
