package portals

trait TaskStates:
  def perKey[T](name: String, initValue: T)(using TaskContext[_, _]): PerKeyState[T]
  def perTask[T](name: String, initValue: T)(using TaskContext[_, _]): PerTaskState[T]
end TaskStates // trait

object TaskStates extends TaskStates:
  override def perKey[T](name: String, initValue: T)(using TaskContext[_, _]): PerKeyState[T] =
    PerKeyState(name, initValue)

  override def perTask[T](name: String, initValue: T)(using TaskContext[_, _]): PerTaskState[T] =
    PerTaskState(name, initValue)
end TaskStates // object

trait TypedState[T]:
  def get(): T
  def set(value: T): Unit
  def del(): Unit
end TypedState // trait

trait PerKeyState[T] extends TypedState[T]

trait PerTaskState[T] extends TypedState[T]

object PerKeyState:
  def apply[T](name: String, initValue: T)(using TaskContext[_, _]): PerKeyState[T] =
    PerKeyStateImpl[T](name, initValue)
end PerKeyState // object

object PerTaskState:
  def apply[T](name: String, initValue: T)(using TaskContext[_, _]): PerTaskState[T] =
    PerTaskStateImpl[T](name, initValue)
end PerTaskState // object

class PerKeyStateImpl[T](name: String, initValue: T)(using TaskContext[_, _]) extends PerKeyState[T]:
  private val _state: TaskState[Any, Any] = summon[TaskContext[_, _]].state

  override def get(): T = _state.get(name) match
    case Some(value) => value.asInstanceOf[T]
    case None => initValue

  override def set(value: T): Unit = _state.set(name, value)

  override def del(): Unit = _state.del(name)

end PerKeyStateImpl // class

class PerTaskStateImpl[T](name: String, initValue: T)(using TaskContext[_, _]) extends PerTaskState[T]:
  private val _state: TaskState[Any, Any] = summon[TaskContext[_, _]].state

  private var _key: Key[Long] = _
  private val _reservedKey: Key[Long] = Key(-2) // reserved to TaskState for now :)

  private def setKey(): Unit =
    // TODO: consider having perTaskState and perKeyState be disjoint states so we don't have to manipulate the key here
    // override the key to per task key
    _key = _state.key
    _state.key = _reservedKey

  private def resetKey(): Unit =
    // reset the key
    _state.key = _key

  override def get(): T =
    setKey()
    val res = _state.get(name) match
      case Some(value) => value.asInstanceOf[T]
      case None => initValue
    resetKey()
    res

  override def set(value: T): Unit =
    setKey()
    _state.set(name, value)
    resetKey()

  override def del(): Unit =
    setKey()
    _state.del(name)
    resetKey()

end PerTaskStateImpl // class

// TODO: implement more efficient Map interface
extension [K, V](state: PerTaskState[Map[K, V]]) {
  def get(key: K): Option[V] = state.get().get(key)

  def update(key: K, value: V): Unit =
    state.set(
      (state.get() + (key -> value))
    )

  def remove(key: K): Unit =
    state.set(
      (state.get() - key)
    )
}
