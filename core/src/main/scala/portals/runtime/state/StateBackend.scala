package portals.runtime.state

trait StateBackend extends Snapshottable[Snapshot]:
  def get(key: Any): Option[Any]
  def set(key: Any, value: Any): Unit
  def del(key: Any): Unit
  def clear(): Unit
  def iterator: Iterator[(Any, Any)]
