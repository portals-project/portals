package portals.system.test

import portals.Application

// trait TestSystem:
//   /** Launch a Portals application. */
//   def launch(application: Application): Unit

//   def step(): Unit

//   def stepUntilComplete(): Unit

//   /** Shutdown the system. */
//   def shutdown(): Unit

class TestSystem():
  val runtime = TestRuntime()

  def launch(application: Application): Unit = runtime.launch(application)

  def step(): Unit = runtime.step()

  def stepUntilComplete(): Unit = runtime.stepUntilComplete()

  def shutdown(): Unit = runtime.shutdown()
