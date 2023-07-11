package portals.util

trait Key:
  val x: Long

object Key:
  def apply(_x: Long): Key = new Key { val x = _x }
