package portals.util

trait Serializer:
  def serialize[T](obj: T): Array[Byte]

  def deserialize[T](bytes: Array[Byte]): T
