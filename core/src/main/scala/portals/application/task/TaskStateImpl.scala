package portals.application.task

import portals.*
import portals.application.task.TaskState
import portals.runtime.state.MapStateBackendImpl
import portals.runtime.state.StateBackend

private[portals] class TaskStateImpl[K, V] extends TaskState[K, V]:
  private[portals] var stateBackend: StateBackend = new MapStateBackendImpl()

  override def get(k: K): Option[V] = stateBackend.get(keyBuilder(k)).asInstanceOf[Option[V]]

  override def set(k: K, v: V): Unit = stateBackend.set(keyBuilder(k), v)

  override def del(k: K): Unit = stateBackend.del(keyBuilder(k))

  override def clear(): Unit = stateBackend.clear()

  override def iterator: Iterator[(K, V)] = stateBackend.iterator.asInstanceOf[Iterator[(K, V)]]

  private[portals] var path: String = _

  private[portals] var key: Key[Long] = _

  private def keyBuilder(k: K): (String, K) = (path + "$" + key.x.toString(), k)
end TaskStateImpl // class
