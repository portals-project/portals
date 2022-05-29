package pods.workflows

private[pods] class TaskStateImpl[K, V] extends TaskState[K, V]:
  private var map: Map[K, V] = Map.empty
  def get(k: K): Option[V] = map.get(k)
  def set(k: K, v: V): Unit = map += (k -> v)
  def del(k: K): Unit = map -= k
  def clear(): Unit = map = Map.empty
  def iterator: Iterator[(K, V)] = map.iterator