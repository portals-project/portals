package portals.application.task

import portals.application.task.StatefulTaskContext
import portals.application.task.TaskState
import portals.util.Key

////////////////////////////////////////////////////////////////////////////////
// Task States
////////////////////////////////////////////////////////////////////////////////

trait TaskStates:
  def perKey[T](name: String, initValue: T): PerKeyState[T]
  def perTask[T](name: String, initValue: T): PerTaskState[T]
end TaskStates // trait

object TaskStates extends TaskStates:
  override def perKey[T](name: String, initValue: T): PerKeyState[T] =
    PerKeyState(name, initValue)

  override def perTask[T](name: String, initValue: T): PerTaskState[T] =
    PerTaskState(name, initValue)
end TaskStates // object

////////////////////////////////////////////////////////////////////////////////
// Typed State
////////////////////////////////////////////////////////////////////////////////

trait TypedState[T]:
  def get()(using StatefulTaskContext): T
  def set(value: T)(using StatefulTaskContext): Unit
  def del()(using StatefulTaskContext): Unit
end TypedState // trait

////////////////////////////////////////////////////////////////////////////////
// Per Key State
////////////////////////////////////////////////////////////////////////////////

trait PerKeyState[T] extends TypedState[T]

object PerKeyState:
  def apply[T](name: String, initValue: T): PerKeyState[T] =
    PerKeyStateImpl[T](name, initValue)
end PerKeyState // object

class PerKeyStateImpl[T](name: String, initValue: T) extends PerKeyState[T]:
  private val _state: StatefulTaskContext ?=> TaskState[Any, Any] = summon[StatefulTaskContext].state

  override def get()(using StatefulTaskContext): T = _state.get(name) match
    case Some(value) => value.asInstanceOf[T]
    case None => initValue

  override def set(value: T)(using StatefulTaskContext): Unit = _state.set(name, value)

  override def del()(using StatefulTaskContext): Unit = _state.del(name)

end PerKeyStateImpl // class

////////////////////////////////////////////////////////////////////////////////
// Per Task State
////////////////////////////////////////////////////////////////////////////////

trait PerTaskState[T] extends TypedState[T]

object PerTaskState:
  def apply[T](name: String, initValue: T): PerTaskState[T] =
    PerTaskStateImpl[T](name, initValue)
end PerTaskState // object

class PerTaskStateImpl[T](name: String, initValue: T) extends PerTaskState[T]:
  private val _state: StatefulTaskContext ?=> TaskState[Any, Any] = summon[StatefulTaskContext].state

  private var _key: Key[Long] = _
  private val _reservedKey: Key[Long] = Key(-2) // reserved to TaskState for now :)

  private def setKey()(using StatefulTaskContext): Unit =
    // TODO: consider having perTaskState and perKeyState be disjoint states so we don't have to manipulate the key here
    // override the key to per task key
    _key = _state.key
    _state.key = _reservedKey

  private def resetKey()(using StatefulTaskContext): Unit =
    // reset the key
    _state.key = _key

  override def get()(using StatefulTaskContext): T =
    setKey()
    val res = _state.get(name) match
      case Some(value) => value.asInstanceOf[T]
      case None => initValue
    resetKey()
    res

  override def set(value: T)(using StatefulTaskContext): Unit =
    setKey()
    _state.set(name, value)
    resetKey()

  override def del()(using StatefulTaskContext): Unit =
    setKey()
    _state.del(name)
    resetKey()

end PerTaskStateImpl // class

////////////////////////////////////////////////////////////////////////////////
// Various State Extensions
////////////////////////////////////////////////////////////////////////////////

// map extension
object MapTaskStateExtension:
  extension [K, V](state: PerTaskState[Map[K, V]]) {
    def get(key: K)(using StatefulTaskContext): Option[V] = state.get().get(key)

    def update(key: K, value: V)(using StatefulTaskContext): Unit =
      state.set(
        (state.get() + (key -> value))
      )

    def remove(key: K)(using StatefulTaskContext): Unit =
      state.set(
        (state.get() - key)
      )
  }

  extension [K, V](state: PerKeyState[Map[K, V]]) {
    def get(key: K)(using StatefulTaskContext): Option[V] = state.get().get(key)

    def update(key: K, value: V)(using StatefulTaskContext): Unit =
      state.set(
        (state.get() + (key -> value))
      )

    def remove(key: K)(using StatefulTaskContext): Unit =
      state.set(
        (state.get() - key)
      )
  }
