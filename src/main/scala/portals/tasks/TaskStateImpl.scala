package portals

private[portals] class TaskStateImpl[K, V] extends TaskState[K, V]:
  private var map: Map[K, V] = Map.empty
  override def get(k: K): Option[V] = map.get(k)
  override def set(k: K, v: V): Unit = map += (k -> v)
  override def del(k: K): Unit = map -= k
  override def clear(): Unit = map = Map.empty
  override def iterator: Iterator[(K, V)] = map.iterator
