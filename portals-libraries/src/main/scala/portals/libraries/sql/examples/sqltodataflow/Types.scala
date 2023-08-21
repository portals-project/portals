package portals.libraries.sql.examples.sqltodataflow

import portals.libraries.sql.internals.*

/** Types used within the context of the SQLToDataflow example. */
object Types:
  case class KV(k: Integer, v: Integer)

  object KVSerializable extends DBSerializable[KV]:
    override def fromObjectArray(arr: List[Any]): KV =
      KV(arr(0).asInstanceOf[Integer], arr(1).asInstanceOf[Integer])

    override def toObjectArray(kv: KV): Array[Object] =
      Array(kv.k.asInstanceOf[Object], kv.v.asInstanceOf[Object])
  given DBSerializable[KV] = KVSerializable
