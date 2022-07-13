package portals

trait Key[T]:
  val x: T

object Key:
  def apply[T](_x: T): Key[T] = new Key{ val x = _x}