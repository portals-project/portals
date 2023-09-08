package portals.runtime

import portals.application.Application

trait SteppingRuntime extends PortalsRuntime:
  /** Returns true if the runtime can take another step. */
  def canStep(): Boolean

  /** Take a step.
    *
    * A **step** will randomly choose one of the processing units (Workflows,
    * Sequencers, etc.) with valid input. It will take one atom from the input
    * and process it to completion.
    *
    * Throws an exception if it cannot take a step.
    */
  def step(): Unit

  /** Take steps until cannot take more steps. */
  def stepUntilComplete(): Unit =
    while canStep() do step()

  /** Take steps until either cannot take more steps or has reached the max. */
  def stepUntilComplete(max: Int): Unit =
    var i = 0
    while canStep() && i < max do
      step()
      i += 1

  /** Take steps for `millis` milliseconds and then stop. */
  def stepFor(millis: Long): Unit =
    val start = System.currentTimeMillis()
    while System.currentTimeMillis() - start < millis do
      if canStep()
      then step()
      else Thread.sleep(100)

end SteppingRuntime
