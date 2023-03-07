package portals.application.task

import portals.*
import portals.application.task.TaskStateImpl
import portals.util.Key

private[portals] trait TaskState[K, V]:
  /** get the value of the key, scoped by the dynamic invocation context */
  def get(k: K): Option[V]

  /** set the key to the value, scoped by the dynamic invocation context */
  def set(k: K, v: V): Unit

  /** delete the key, scoped by the dynamic invocation context */
  def del(k: K): Unit

  /** iterate over all key-value pairs, **NOT** scoped by the dynamic context
    */
  def iterator: Iterator[(K, V)]

  /** clear the state of the current instance, **NOT** scoped by the dynamic
    * context
    */
  def clear(): Unit

  //////////////////////////////////////////////////////////////////////////////
  // Execution Context
  //////////////////////////////////////////////////////////////////////////////

  /** Path of the task */
  // has to be var so that it can be swapped at runtime
  private[portals] var path: String

  /** Contextual key for per-key execution */
  // has to be var so that it can be swapped at runtime
  private[portals] var key: Key[Long]

end TaskState // trait

private[portals] object TaskState:
  def apply(): TaskState[Any, Any] =
    new TaskStateImpl

end TaskState // object
