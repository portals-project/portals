package portals.libraries.sql.examples.sqltodataflow

import portals.application.generator.Generators
import portals.libraries.sql.examples.sqltodataflow.Config.N_ATOM_SIZE
import portals.libraries.sql.internals.COMMIT_QUERY
import portals.libraries.sql.internals.TxnQuery
import portals.system.TestSystem

object Data:
  import Config.*

  private val rand = new scala.util.Random

  //////////////////////////////////////////////////////////////////////////////
  // QUERY TYPES
  //////////////////////////////////////////////////////////////////////////////

  private inline def insert_into(table: String, k: Int, v: Int): String =
    s"INSERT INTO $table (k, v) Values ($k, $v)"

  private inline def select_from(table: String, k: Int): String =
    s"SELECT * FROM $table WHERE k = $k"

  private inline def select_from_where(table: String, v: Int, ks: List[Int]): String =
    s"SELECT * FROM $table WHERE v = $v AND k in (${ks.mkString(", ")})"

  //////////////////////////////////////////////////////////////////////////////
  // NON TRANSACTIONAL
  //////////////////////////////////////////////////////////////////////////////

  private def queries: Iterator[Iterator[String]] =
    Iterator
      // info: to make the iterator infinite, use `Iterator.continually` instead
      .fill(N_EVENTS) {
        rand.nextInt(3) match
          case 0 =>
            insert_into("KVTable", rand.nextInt(N_KEYS), rand.nextInt(N_VALUES))
          case 1 =>
            select_from("KVTable", rand.nextInt(N_KEYS))
          case 2 =>
            select_from_where("KVTable", rand.nextInt(N_VALUES), List.fill(6)(rand.nextInt(N_KEYS)))
      }
      .grouped(N_ATOM_SIZE)
      .map(_.iterator)

  def queriesGenerator =
    ThrottledGenerator(
      Generators.fromIteratorOfIterators(queries),
      RATE_LIMIT,
    )

  // private val queries =
  //   List(
  //     List(
  //       "INSERT INTO KVTable (k, v) Values (0, 0)",
  //       "INSERT INTO KVTable (k, v) Values (1, 1)",
  //       "INSERT INTO KVTable (k, v) Values (2, 2)",
  //       "INSERT INTO KVTable (k, v) Values (3, 0)",
  //       "INSERT INTO KVTable (k, v) Values (4, 1)",
  //       "INSERT INTO KVTable (k, v) Values (5, 2)",
  //     ),
  //     List(
  //       "SELECT * FROM KVTable WHERE k = 0",
  //       "SELECT * FROM KVTable WHERE k = 1",
  //       "SELECT * FROM KVTable WHERE k = 2",
  //       "SELECT * FROM KVTable WHERE k = 3",
  //       "SELECT * FROM KVTable WHERE k = 4",
  //       "SELECT * FROM KVTable WHERE k = 5",
  //     ),
  //     List(
  //       "SELECT * FROM KVTable WHERE v = 0 AND k in (0, 1, 2, 3, 4, 5)",
  //       "SELECT * FROM KVTable WHERE v = 1 AND k in (0, 1, 2, 3, 4, 5)",
  //       "SELECT * FROM KVTable WHERE v = 2 AND k in (0, 1, 2, 3, 4, 5)",
  //     )
  //   )

  // def queryIterOfIter: Iterator[Iterator[String]] =
  //   queries.map(_.iterator).iterator

  //////////////////////////////////////////////////////////////////////////////
  // TRANSACTIONAL
  //////////////////////////////////////////////////////////////////////////////

  private def transactionalQueries: Iterator[Iterator[TxnQuery]] =
    Iterator
      .fill(N_EVENTS) {
        rand.nextInt(2) match
          case 0 =>
            val txid = rand.nextInt()
            Iterator(
              TxnQuery(insert_into("KVTable", rand.nextInt(N_KEYS), rand.nextInt(N_VALUES)), txid),
              TxnQuery(insert_into("KVTable", rand.nextInt(N_KEYS), rand.nextInt(N_VALUES)), txid),
              TxnQuery(COMMIT_QUERY, txid),
            )
          case 1 =>
            val txid = rand.nextInt()
            Iterator(
              TxnQuery(select_from("KVTable", rand.nextInt(N_KEYS)), txid),
              TxnQuery(COMMIT_QUERY, txid),
            )
      }

  def transactionalQueriesGenerator =
    ThrottledGenerator(
      Generators.fromIteratorOfIterators(transactionalQueries),
      RATE_LIMIT,
    )

    // // Note: Not interactive query here, possible
    // private val transactionalQueries1 =
    //   List(
    //     List(
    //       TxnQuery("INSERT INTO KVTable (k, v) Values (0, 0)", 1),
    //     ),
    //     List(
    //       TxnQuery("INSERT INTO KVTable (k, v) Values (1, 0)", 1),
    //     ),
    //     List(
    //       TxnQuery(COMMIT_QUERY, 1),
    //     ),
    //     List(
    //       TxnQuery("SELECT * FROM KVTable WHERE k in (0, 1)", 2),
    //     ),
    //     List(
    //       TxnQuery(COMMIT_QUERY, 2),
    //     ),
    //   )

    // private val transactionalQueries2 =
    //   List(
    //     List(
    //       TxnQuery("INSERT INTO KVTable (k, v) Values (0, 1)", 3),
    //     ),
    //     List(
    //       TxnQuery("INSERT INTO KVTable (k, v) Values (1, 1)", 3),
    //     ),
    //     List(
    //       TxnQuery(COMMIT_QUERY, 3),
    //     ),
    //     List(
    //       TxnQuery("SELECT * FROM KVTable WHERE k in (0, 1)", 4),
    //     ),
    //     List(
    //       TxnQuery(COMMIT_QUERY, 4),
    //     ),
    //   )

    // def queryIterOfIterTxn1: Iterator[Iterator[TxnQuery]] =
    //   transactionalQueries1.map(_.iterator).iterator

    // def queryIterOfIterTxn2: Iterator[Iterator[TxnQuery]] =
    //   transactionalQueries2.map(_.iterator).iterator

  //////////////////////////////////////////////////////////////////////////////
  // UTILITIES
  //////////////////////////////////////////////////////////////////////////////

  extension (t: TestSystem) {
    def stepFor(millis: Long): Unit =
      val start = System.currentTimeMillis()
      while System.currentTimeMillis() - start < millis do if t.canStep() then t.step() else Thread.sleep(100)
  }
