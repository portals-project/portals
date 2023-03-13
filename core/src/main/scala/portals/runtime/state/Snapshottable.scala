package portals.runtime.state

trait Snapshottable[S <: Snapshot]:
  def snapshot(): S
  def incrementalSnapshot(): S
