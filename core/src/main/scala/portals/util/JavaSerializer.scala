package portals

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object JavaSerializer extends Serializer {
  // TODO: consider using `spores3` instead

  override def serialize[T](obj: T): Array[Byte] =
    val bytes = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(bytes)
    out.writeObject(obj)
    out.close()
    bytes.toByteArray

  override def deserialize[T](bytes: Array[Byte]): T =
    val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
    in.readObject().asInstanceOf[T]
}
