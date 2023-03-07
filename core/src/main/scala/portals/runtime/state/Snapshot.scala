package portals.runtime.state

trait Snapshot:
  def iterator: Iterator[(Any, Any)]
