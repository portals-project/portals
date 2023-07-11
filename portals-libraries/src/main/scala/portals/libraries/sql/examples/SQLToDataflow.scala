package portals.libraries.sql.examples

import portals.api.dsl.DSL.*
import portals.api.dsl.ExperimentalDSL.*
import portals.application.AtomicPortalRef
import portals.libraries.sql.*
import portals.libraries.sql.queryable.*
import portals.system.Systems

/** An example with a queryable Key-Value table using the sql library.
  *
  * @example
  *   {{{
  *  sbt "libraries/runMain portals.libraries.sql.examples.SQLToDataflow"
  *   }}}
  */
object SQLToDataflow extends App:
  import SQLLibraryExtensions.*

  /** Types used within the context of the SQLToDataflow example. */
  object Types:
    case class KV(k: Integer, v: Integer)

    object KVSerializable extends DBSerializable[KV]:
      override def fromObjectArray(arr: List[Any]): KV =
        KV(arr(0).asInstanceOf[Integer], arr(1).asInstanceOf[Integer])

      override def toObjectArray(kv: KV): Array[Object] =
        Array(kv.k.asInstanceOf[Object], kv.v.asInstanceOf[Object])
    given DBSerializable[KV] = KVSerializable
  import Types.*
  import Types.given

  /** Portals application which runs the queriable KV Table. */
  val tableApp = PortalsApp("SQLToDataflowTable"):
    val table = Table[KV]("KVTable", "k")

    val generator = Generators.fromListOfLists[String](
      List(
        List(
          "INSERT INTO KVTable (k, v) Values (0, 0)",
          "INSERT INTO KVTable (k, v) Values (1, 1)",
          "INSERT INTO KVTable (k, v) Values (2, 2)",
          "INSERT INTO KVTable (k, v) Values (3, 0)",
          "INSERT INTO KVTable (k, v) Values (4, 1)",
          "INSERT INTO KVTable (k, v) Values (5, 2)",
        ),
        List(
          "SELECT * FROM KVTable WHERE k = 0",
          "SELECT * FROM KVTable WHERE k = 1",
          "SELECT * FROM KVTable WHERE k = 2",
          "SELECT * FROM KVTable WHERE k = 3",
          "SELECT * FROM KVTable WHERE k = 4",
          "SELECT * FROM KVTable WHERE k = 5",
        ),
        List(
          "SELECT * FROM KVTable WHERE v = 0 AND k in (0, 1, 2, 3, 4, 5)",
          "SELECT * FROM KVTable WHERE v = 1 AND k in (0, 1, 2, 3, 4, 5)",
          "SELECT * FROM KVTable WHERE v = 2 AND k in (0, 1, 2, 3, 4, 5)",
        )
      )
    )

    val queryWorkflow = Workflows[String, String]("queryWorkflow")
      .source(generator.stream)
      .query(table)
      .sink()
      .freeze()

  val system = Systems.test()
  system.launch(tableApp)
  system.stepUntilComplete()
  system.shutdown()

object SQLToDataflow2 extends App:
  import SQLLibraryExtensions.*

  /** Types used within the context of the SQLToDataflow example. */
  object Types:
    case class KV(k: Integer, v: Integer)

    object KVSerializable extends DBSerializable[KV]:
      override def fromObjectArray(arr: List[Any]): KV =
        KV(arr(0).asInstanceOf[Integer], arr(1).asInstanceOf[Integer])

      override def toObjectArray(kv: KV): Array[Object] =
        Array(kv.k.asInstanceOf[Object], kv.v.asInstanceOf[Object])
    given DBSerializable[KV] = KVSerializable
  import Types.*
  import Types.given

  /** Portals application which runs the queriable KV Table. */
  val tableApp = PortalsApp("SQLToDataflowTable"):
    val table = Table[KV]("KVTable", "k")

    val queryPortal = QueryPortal("queryPortal", table)

    val generator = Generators.fromListOfLists[String](
      List(
        List(
          "INSERT INTO KVTable (k, v) Values (0, 0)",
          "INSERT INTO KVTable (k, v) Values (1, 1)",
          "INSERT INTO KVTable (k, v) Values (2, 2)",
          "INSERT INTO KVTable (k, v) Values (3, 0)",
          "INSERT INTO KVTable (k, v) Values (4, 1)",
          "INSERT INTO KVTable (k, v) Values (5, 2)",
        ),
        List(
          "SELECT * FROM KVTable WHERE k = 0",
          "SELECT * FROM KVTable WHERE k = 1",
          "SELECT * FROM KVTable WHERE k = 2",
          "SELECT * FROM KVTable WHERE k = 3",
          "SELECT * FROM KVTable WHERE k = 4",
          "SELECT * FROM KVTable WHERE k = 5",
        ),
        List(
          "SELECT * FROM KVTable WHERE v = 0 AND k in (0, 1, 2, 3, 4, 5)",
          "SELECT * FROM KVTable WHERE v = 1 AND k in (0, 1, 2, 3, 4, 5)",
          "SELECT * FROM KVTable WHERE v = 2 AND k in (0, 1, 2, 3, 4, 5)",
        )
      )
    )

    val queryWorkflow = Workflows[String, String]("queryWorkflow")
      .source(generator.stream)
      .asker(queryPortal.portal) { x =>
        val f = ask(queryPortal.portal)(x)
        await(f) {
          emit(f.value.get)
        }
      }
      .logger()
      .sink()
      .freeze()

  val system = Systems.test()
  system.launch(tableApp)
  system.stepUntilComplete()
  system.shutdown()
