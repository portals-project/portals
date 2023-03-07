package portals.runtime.state

private[portals] class MapStateBackendImpl extends StateBackend:
  private val map = scala.collection.mutable.Map[Any, Any]()
  override def get(key: Any): Option[Any] = map.get(key)
  override def set(key: Any, value: Any): Unit = map.put(key, value)
  override def del(key: Any): Unit = map.remove(key)
  override def clear(): Unit = map.clear()
  override def iterator: Iterator[(Any, Any)] = map.iterator
  override def snapshot(): Snapshot = { val c = map.clone(); new Snapshot { def iterator = c.iterator } }
  override def incrementalSnapshot(): Snapshot = new Snapshot { def iterator = Iterator.empty }
