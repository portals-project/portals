package portals

private[portals] trait TaskState[K, V]:
  /** get the value of the key, scoped by the dynamic invocation context */
  def get(k: K): Option[V]

  /** set the key to the value, scoped by the dynamic invocation context */
  def set(k: K, v: V): Unit

  /** delete the key, scoped by the dynamic invocation context */
  def del(k: K): Unit

  /** iterate over all key-value pairs, **NOT** scoped by the dynamic invocation context */
  def iterator: Iterator[(K, V)]

  /** clear the state of the current instance, **NOT** scoped by the dynamic invocation context */
  def clear(): Unit

  //////////////////////////////////////////////////////////////////////////////
  // Execution Context
  //////////////////////////////////////////////////////////////////////////////

  /** Path of the task */
  // has to be var so that it can be swapped at runtime
  // TODO: this will be used once we are sharing state on some state backend, such as RocksDB
  private[portals] var path: String // TODO: make this set by the runtime

  /** Contextual key for per-key execution */
  // has to be var so that it can be swapped at runtime
  private[portals] var key: Key[Long]

end TaskState // trait

private[portals] object TaskState:
  def apply[K, V](): TaskState[K, V] =
    new TaskStateImpl[K, V]

end TaskState // object