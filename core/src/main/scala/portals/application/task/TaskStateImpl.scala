package portals.application.task

import portals.application.task.TaskState
import portals.runtime.state.RocksDBStateBackendImpl
import portals.runtime.state.StateBackend
import portals.util.Key

private[portals] class TaskStateImpl extends TaskState[Any, Any]:
  private[portals] var stateBackend: StateBackend = new RocksDBStateBackendImpl()

  override def get(k: Any): Option[Any] = stateBackend.get(keyBuilder(k)).asInstanceOf[Option[Any]]

  override def set(k: Any, v: Any): Unit = stateBackend.set(keyBuilder(k), v)

  override def del(k: Any): Unit = stateBackend.del(keyBuilder(k))

  override def clear(): Unit = stateBackend.clear()

  override def iterator: Iterator[(Any, Any)] = stateBackend.iterator.asInstanceOf[Iterator[(Any, Any)]]

  private[portals] var path: String = _

  private[portals] var key: Key = _

  private def keyBuilder(k: Any): (String, Any) = (path + "$" + key.x.toString(), k)
end TaskStateImpl // class
