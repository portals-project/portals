package portals.application.task

import portals.*
import portals.application.task.TaskState

private[portals] class TaskStateImpl[K, V] extends TaskState[K, V]:
  private var map: Map[(String, K), V] = Map.empty

  override def get(k: K): Option[V] = map.get(keyBuilder(k))

  override def set(k: K, v: V): Unit = map += (keyBuilder(k) -> v)

  override def del(k: K): Unit = map -= keyBuilder(k)

  override def clear(): Unit = map = Map.empty

  override def iterator: Iterator[(K, V)] = map.iterator.map(kv => (kv._1._2, kv._2))

  private[portals] var path: String = _

  private[portals] var key: Key[Long] = _

  private def keyBuilder(k: K): (String, K) = (path + "$" + key.x.toString(), k)
end TaskStateImpl // class
