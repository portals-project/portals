package pods.workflows

private[pods] trait TaskState[K, V]:
  def get(k: K): Option[V]
  def set(k: K, v: V): Unit
  def del(k: K): Unit
  def iterator: Iterator[(K, V)]
  def clear(): Unit

private[pods] object TaskState:
  def apply[K, V](): TaskState[K, V] =
    new TaskStateImpl[K, V]
