package pods.workflows

private[pods] trait TaskState[K, V]:
  def get(k: K): Option[V]

  def set(k: K, v: V): Unit
  
  def del(k: K): Unit
  
  def clear(): Unit
