package pods.workflows

private[pods] sealed trait TaskState[K, V]:
  def get(k: K): Option[V]
  def set(k: K, v: V): Unit
  def del(k: K): Unit

private[pods] class TaskStateImpl[K, V] extends TaskState[K, V]:
  private var map: Map[K, V] = Map.empty
  def get(k: K): Option[V] = map.get(k)
  def set(k: K, v: V): Unit = map += (k -> v)
  def del(k: K): Unit = map -= k

private[pods] object TaskState:
  def apply[K, V](): TaskState[K, V] =
    new TaskStateImpl[K, V]
